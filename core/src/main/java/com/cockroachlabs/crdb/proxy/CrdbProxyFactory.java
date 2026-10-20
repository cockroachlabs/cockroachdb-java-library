package com.cockroachlabs.crdb.proxy;

import com.cockroachlabs.crdb.config.CrdbConfig;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;

/**
 * Produces enhanced instances of annotated targets.
 *
 * <ul>
 *   <li>If the target implements at least one interface, a JDK
 *       {@link Proxy} is created over those interfaces and delegates to the
 *       original instance. <b>Note:</b> the returned object is the interface
 *       type, so assign it to an interface-typed reference.</li>
 *   <li>Otherwise a ByteBuddy subclass of the concrete class is generated; the
 *       target's field state is copied into the new instance.</li>
 * </ul>
 */
public final class CrdbProxyFactory {

    private final CrdbConfig config;

    public CrdbProxyFactory() {
        this(CrdbConfig.defaults());
    }

    public CrdbProxyFactory(CrdbConfig config) {
        this.config = config;
    }

    /** Enhance {@code target}, choosing the proxy strategy automatically. */
    @SuppressWarnings("unchecked")
    public <T> T enhance(T target) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        CrdbDispatcher dispatcher = new CrdbDispatcher(config);
        Class<?> type = target.getClass();
        Class<?>[] interfaces = allInterfaces(type);
        if (interfaces.length > 0) {
            return (T) interfaceProxy(target, interfaces, dispatcher);
        }
        return (T) classProxy(target, type, dispatcher);
    }

    // ---- interface (JDK Proxy) strategy ----------------------------------

    private static Object interfaceProxy(Object target, Class<?>[] interfaces, CrdbDispatcher dispatcher) {
        InvocationHandler handler = (proxy, method, args) -> {
            Method concrete = resolveConcrete(target.getClass(), method);
            OriginalCall original = () -> invokeReflectively(target, concrete, args);
            try {
                return dispatcher.dispatch(concrete, original);
            } catch (Exception e) {
                throw unwrapForCaller(e);
            }
        };
        return Proxy.newProxyInstance(target.getClass().getClassLoader(), interfaces, handler);
    }

    private static Object invokeReflectively(Object target, Method method, Object[] args) throws Exception {
        try {
            method.setAccessible(true);
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception ex) {
                throw ex;
            }
            if (cause instanceof Error err) {
                throw err;
            }
            throw new RuntimeException(cause);
        }
    }

    private static Method resolveConcrete(Class<?> targetType, Method interfaceMethod) {
        try {
            return targetType.getMethod(interfaceMethod.getName(), interfaceMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            return interfaceMethod;
        }
    }

    private static RuntimeException unwrapForCaller(Exception e) {
        if (e instanceof RuntimeException re) {
            return re;
        }
        return new RuntimeException(e);
    }

    // ---- class (ByteBuddy subclass) strategy -----------------------------

    private static Object classProxy(Object target, Class<?> type, CrdbDispatcher dispatcher) {
        if (Modifier.isFinal(type.getModifiers())) {
            throw new UnsupportedOperationException(
                    "Cannot enhance final class " + type.getName()
                            + "; extract an interface or remove 'final'.");
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
            Class<?> proxyType = new ByteBuddy()
                    .subclass(type)
                    .method(isAnnotatedWith(com.cockroachlabs.crdb.annotations.Transactional.class)
                            .or(isAnnotatedWith(com.cockroachlabs.crdb.annotations.CrdbRetry.class))
                            .or(isAnnotatedWith(com.cockroachlabs.crdb.annotations.CircuitBreaker.class))
                            .or(isAnnotatedWith(com.cockroachlabs.crdb.annotations.Region.class))
                            .or(isAnnotatedWith(com.cockroachlabs.crdb.annotations.VirtualCluster.class))
                            .or(isAnnotatedWith(com.cockroachlabs.crdb.annotations.Workload.class))
                            .or(isAnnotatedWith(com.cockroachlabs.crdb.annotations.FollowerRead.class)))
                    .intercept(MethodDelegation.to(new Interceptor(dispatcher)))
                    .make()
                    // Define the subclass in the target's own runtime package so
                    // it can access package-private members / super methods.
                    .load(type.getClassLoader(), ClassLoadingStrategy.UsingLookup.of(lookup))
                    .getLoaded();
            Object instance = instantiate(proxyType);
            copyFields(target, instance, type);
            return instance;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create class proxy for " + type.getName(), e);
        }
    }

    private static Object instantiate(Class<?> proxyType) throws Exception {
        var ctor = proxyType.getDeclaredConstructor();
        ctor.setAccessible(true);
        return ctor.newInstance();
    }

    private static void copyFields(Object from, Object to, Class<?> type) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) {
                    continue;
                }
                try {
                    f.setAccessible(true);
                    f.set(to, f.get(from));
                } catch (ReflectiveOperationException ignored) {
                    // Skip fields we cannot copy; state may be partially carried.
                }
            }
        }
    }

    /**
     * ByteBuddy interception target. Public so the generated subclass can bind
     * to it; delegates to the shared {@link CrdbDispatcher}.
     */
    public static final class Interceptor {

        private final CrdbDispatcher dispatcher;

        Interceptor(CrdbDispatcher dispatcher) {
            this.dispatcher = dispatcher;
        }

        @RuntimeType
        public Object intercept(@Origin Method method,
                                @SuperCall Callable<Object> zuper,
                                @AllArguments Object[] args) throws Exception {
            return dispatcher.dispatch(method, zuper::call);
        }
    }

    // ---- helpers ---------------------------------------------------------

    private static Class<?>[] allInterfaces(Class<?> type) {
        Set<Class<?>> found = new LinkedHashSet<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            collect(c, found);
        }
        return found.toArray(new Class<?>[0]);
    }

    private static void collect(Class<?> type, Set<Class<?>> out) {
        for (Class<?> i : type.getInterfaces()) {
            if (out.add(i)) {
                collect(i, out);
            }
        }
    }
}
