package com.cockroachlabs.crdb;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the one null-safety convention NullAway can't: every package of the library (across all
 * modules) carries a {@code @NullMarked} {@code package-info}, so consumers of the published jars
 * see the non-null-by-default contract.
 *
 * <p>This is the faithful multi-module port of the Spring starter's {@code NullSafetyConventionsTest}.
 * The starter's two Spring-specific checks (override nullness, {@code @Around} advice returning
 * {@code @Nullable}) are N/A here: the Java library has no Spring or AspectJ surface.
 *
 * <p>It loads the compiled classes of every module, with the optional dependencies they reference,
 * in a class loader of their own (from the {@code nullSafety.classpath} system property that the
 * Gradle build sets on core's {@code test} task), and reads JSpecify's annotations, which are
 * retained at runtime. Annotations are matched by name.
 */
class NullSafetyConventionsTest {

    private static final String NULL_MARKED = "org.jspecify.annotations.NullMarked";

    private static final List<Class<?>> classes = new ArrayList<>();
    private static ClassLoader loader = NullSafetyConventionsTest.class.getClassLoader();

    @BeforeAll
    static void loadLibraryClasses() throws IOException {
        String classpath = System.getProperty("nullSafety.classpath");
        assertTrue(classpath != null && !classpath.isBlank(),
                "nullSafety.classpath system property (set by the Gradle build) must be present");
        List<Path> entries = Stream.of(classpath.split(File.pathSeparator)).map(Path::of).toList();
        URL[] urls = new URL[entries.size()];
        for (int i = 0; i < urls.length; i++) {
            urls[i] = entries.get(i).toUri().toURL();
        }
        loader = new URLClassLoader(urls, ClassLoader.getPlatformClassLoader());

        List<String> unloadable = new ArrayList<>();
        for (Path entry : entries) {
            if (Files.isDirectory(entry.resolve("com/cockroachlabs"))) {
                load(entry, unloadable);
            }
        }
        assertTrue(unloadable.isEmpty(), "library classes that can't be loaded: " + unloadable);
        assertFalse(classes.isEmpty(), "no library classes found on nullSafety.classpath");
    }

    private static void load(Path root, List<String> unloadable) throws IOException {
        List<String> names;
        try (Stream<Path> files = Files.walk(root)) {
            names = files.map(root::relativize)
                    .map(Path::toString)
                    .filter(name -> name.endsWith(".class") && name.startsWith("com/cockroachlabs/"))
                    .filter(name -> !name.endsWith("package-info.class"))
                    .map(name -> name.substring(0, name.length() - ".class".length()).replace('/', '.'))
                    .sorted()
                    .toList();
        }
        for (String name : names) {
            try {
                Class<?> type = Class.forName(name, false, loader);
                type.getDeclaredMethods(); // resolves the types in its signatures
                classes.add(type);
            } catch (LinkageError | ClassNotFoundException ex) {
                unloadable.add(name + ": " + ex);
            }
        }
    }

    @Test
    void everyPackageIsNullMarked() {
        Set<String> packages = new TreeSet<>();
        for (Class<?> type : classes) {
            packages.add(type.getPackageName());
        }
        List<String> unmarked = new ArrayList<>();
        for (String name : packages) {
            if (!isNullMarked(name)) {
                unmarked.add(name);
            }
        }
        assertTrue(unmarked.isEmpty(), "packages without a @NullMarked package-info.java: " + unmarked);
    }

    private static boolean isNullMarked(String packageName) {
        try {
            return hasAnnotation(
                    Class.forName(packageName + ".package-info", false, loader).getAnnotations(), NULL_MARKED);
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private static boolean hasAnnotation(Annotation[] annotations, String name) {
        for (Annotation annotation : annotations) {
            if (annotation.annotationType().getName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
