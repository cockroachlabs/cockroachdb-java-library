package com.cockroachlabs.crdb.vcluster;

import com.cockroachlabs.crdb.Crdb;
import com.cockroachlabs.crdb.annotations.Workload.Priority;
import com.cockroachlabs.crdb.context.CrdbContext;
import com.cockroachlabs.crdb.context.CrdbContextValues;
import com.cockroachlabs.crdb.tx.ConnectionCallback;

import javax.sql.DataSource;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * A context-scoped executor for a Cockroach vCluster. Binds the vCluster (and
 * optional workload priority) into {@link CrdbContext} for the duration of the
 * work, so any {@link Crdb#tx(DataSource, ConnectionCallback)} run inside tags
 * the session ({@code SET application_name} + workload QoS) at transaction start.
 *
 * <p><b>Scope (v1):</b> this performs workload <em>tagging</em> and QoS
 * assignment. Routing a workload to a different vCluster endpoint is
 * platform-dependent and is intentionally not faked here — that is future work.
 */
public final class VirtualClusterScope {

    private final String name;
    private final Priority workload;

    public VirtualClusterScope(String name) {
        this(name, null);
    }

    private VirtualClusterScope(String name, Priority workload) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("vCluster name must not be blank");
        }
        this.name = name;
        this.workload = workload;
    }

    /** Return a scope that also tags the workload priority/QoS. */
    public VirtualClusterScope workload(Priority priority) {
        return new VirtualClusterScope(name, priority);
    }

    /** The vCluster name. */
    public String name() {
        return name;
    }

    /** The workload priority, if set. */
    public Optional<Priority> priority() {
        return Optional.ofNullable(workload);
    }

    /** Run {@code body} with this vCluster context bound. */
    public <T> T call(Callable<T> body) {
        return CrdbContext.with(values(), body);
    }

    /** Run {@code body} with this vCluster context bound. */
    public void run(Runnable body) {
        call(() -> {
            body.run();
            return null;
        });
    }

    /**
     * Run a SERIALIZABLE, auto-retrying transaction with this vCluster context
     * bound; the transaction is tagged with {@code application_name} and the
     * mapped workload QoS at start.
     */
    public <T> T tx(DataSource ds, ConnectionCallback<T> work) {
        return call(() -> Crdb.tx(ds, work));
    }

    private CrdbContextValues values() {
        CrdbContextValues v = CrdbContextValues.empty().withVirtualCluster(name);
        if (workload != null) {
            v = v.withWorkload(workload);
        }
        return v;
    }
}
