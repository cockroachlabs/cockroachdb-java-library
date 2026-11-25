package com.cockroachlabs.crdb.context;

import com.cockroachlabs.crdb.annotations.Workload.Priority;

import org.jspecify.annotations.Nullable;

/**
 * Immutable bundle of CockroachDB call context. Any field may be {@code null}
 * meaning "unset".
 *
 * @param region                the region to pin execution to
 * @param virtualCluster        the Cockroach vCluster to route to
 * @param workload              the workload priority class
 * @param followerReadStaleness the follower-read staleness expression, if any
 */
public record CrdbContextValues(
        @Nullable String region,
        @Nullable String virtualCluster,
        @Nullable Priority workload,
        @Nullable String followerReadStaleness) {

    private static final CrdbContextValues EMPTY =
            new CrdbContextValues(null, null, null, null);

    /** The empty context (all fields unset). */
    public static CrdbContextValues empty() {
        return EMPTY;
    }

    public CrdbContextValues withRegion(@Nullable String r) {
        return new CrdbContextValues(r, virtualCluster, workload, followerReadStaleness);
    }

    public CrdbContextValues withVirtualCluster(@Nullable String vc) {
        return new CrdbContextValues(region, vc, workload, followerReadStaleness);
    }

    public CrdbContextValues withWorkload(@Nullable Priority p) {
        return new CrdbContextValues(region, virtualCluster, p, followerReadStaleness);
    }

    public CrdbContextValues withFollowerReadStaleness(@Nullable String s) {
        return new CrdbContextValues(region, virtualCluster, workload, s);
    }

    /**
     * Overlay {@code other} onto this one: any non-null field of {@code other}
     * wins, otherwise this value is retained. Lets nested scopes refine parent
     * context without wiping it.
     */
    public CrdbContextValues merge(@Nullable CrdbContextValues other) {
        if (other == null) {
            return this;
        }
        return new CrdbContextValues(
                other.region != null ? other.region : region,
                other.virtualCluster != null ? other.virtualCluster : virtualCluster,
                other.workload != null ? other.workload : workload,
                other.followerReadStaleness != null
                        ? other.followerReadStaleness : followerReadStaleness);
    }
}
