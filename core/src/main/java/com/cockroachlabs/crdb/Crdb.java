package com.cockroachlabs.crdb;

import com.cockroachlabs.crdb.config.CrdbConfig;
import com.cockroachlabs.crdb.context.CrdbContext;
import com.cockroachlabs.crdb.proxy.CrdbProxyFactory;
import com.cockroachlabs.crdb.spi.AegisClient;
import com.cockroachlabs.crdb.spi.AegisConfig;
import com.cockroachlabs.crdb.spi.AegisFactory;
import com.cockroachlabs.crdb.spi.StorageInsights;
import com.cockroachlabs.crdb.spi.StorageInsightsFactory;
import com.cockroachlabs.crdb.tx.ConnectionCallback;
import com.cockroachlabs.crdb.tx.Isolation;
import com.cockroachlabs.crdb.tx.TransactionTemplate;
import com.cockroachlabs.crdb.vcluster.VirtualClusterScope;

import javax.sql.DataSource;
import java.util.ServiceLoader;
import java.util.logging.Logger;

/**
 * The one-stop entry point for the CockroachDB pure-Java library.
 *
 * <p>Two things you reach for constantly:
 * <ul>
 *   <li>{@link #enhance(Object)} — wrap an annotated object so retry, circuit
 *       breaking, and context "just happen"; the anomaly {@code try/catch}
 *       disappears from your code.</li>
 *   <li>{@link #tx(DataSource, ConnectionCallback)} — the always-available
 *       functional core: a SERIALIZABLE, auto-retrying transaction.</li>
 * </ul>
 *
 * <p>The {@code storage}, {@code vcluster}, and {@code aegis} methods expose the
 * Cockroach Continuum surface (Plenum, vCluster, Aegis); {@code storage}/{@code aegis}
 * providers are discovered from the {@code continuum} module via
 * {@link java.util.ServiceLoader}. The agent/MCP surface lives in the {@code agent}
 * module ({@code CrdbAgent}). See docs/CONTINUUM_PLAN.md.
 */
public final class Crdb {

    private static final Logger LOG = Logger.getLogger(Crdb.class.getName());

    private Crdb() {
    }

    /**
     * Return an enhanced view of {@code target} whose annotated methods gain
     * retry / circuit-breaking / context behavior. See
     * {@link CrdbProxyFactory#enhance(Object)} for the interface-vs-class caveat.
     */
    public static <T> T enhance(T target) {
        return new CrdbProxyFactory().enhance(target);
    }

    /** As {@link #enhance(Object)}, using a specific {@link CrdbConfig}. */
    public static <T> T enhance(T target, CrdbConfig config) {
        return new CrdbProxyFactory(config).enhance(target);
    }

    /**
     * Run {@code work} in a SERIALIZABLE transaction with automatic retry on
     * serialization conflicts. This is the functional core that never needs a
     * proxy or annotations.
     */
    public static <T> T tx(DataSource ds, ConnectionCallback<T> work) {
        return new TransactionTemplate(ds).execute(Isolation.SERIALIZABLE, true, work);
    }

    /** A reusable {@link TransactionTemplate} bound to {@code ds}. */
    public static TransactionTemplate template(DataSource ds) {
        return new TransactionTemplate(ds);
    }

    // ---- Cockroach Continuum surface -------------------------------------

    /**
     * Cockroach Plenum storage insights (read-only usage signals). The provider
     * is discovered from the {@code continuum} module via {@link ServiceLoader}.
     *
     * @throws IllegalStateException if the {@code continuum} module is not on the
     *                               classpath
     */
    public static StorageInsights storage(DataSource ds) {
        if (CrdbContext.virtualCluster().isPresent()) {
            // Platform rule: Cockroach vCluster hosts cannot use Cockroach Plenum.
            LOG.warning("Requesting Cockroach Plenum storage insights while a Cockroach "
                    + "vCluster context is active; vCluster hosts cannot use Plenum, so "
                    + "results may be unavailable or empty.");
        }
        return load(StorageInsightsFactory.class).create(ds);
    }

    /**
     * Cockroach vCluster handle: a context-scoped executor that tags work with a
     * vCluster name and (optionally) a workload priority/QoS.
     */
    public static VirtualClusterScope vcluster(String name) {
        return new VirtualClusterScope(name);
    }

    /**
     * Cockroach Aegis client (autonomous-ops findings/recommendations + exhaust).
     * The provider is discovered from the {@code continuum} module via
     * {@link ServiceLoader}; a disabled/endpoint-less config selects the simulated
     * in-memory backend.
     *
     * @throws IllegalStateException if the {@code continuum} module is not on the
     *                               classpath
     */
    public static AegisClient aegis(AegisConfig config) {
        return load(AegisFactory.class).create(config);
    }

    /**
     * Agent / MCP surface. Use {@code CrdbAgent.from(...)} in the {@code agent}
     * module to build the tool surface.
     *
     * @throws UnsupportedOperationException always; this is a signpost to the
     *         {@code agent} module's {@code CrdbAgent} entry point
     */
    public static Object agent() {
        throw new UnsupportedOperationException(
                "Use CrdbAgent.from(...) in the 'agent' module to build the MCP-style tool surface");
    }

    private static <F> F load(Class<F> spi) {
        return ServiceLoader.load(spi).findFirst().orElseThrow(() ->
                new IllegalStateException("No " + spi.getSimpleName()
                        + " provider found; add the 'continuum' module to the classpath."));
    }
}
