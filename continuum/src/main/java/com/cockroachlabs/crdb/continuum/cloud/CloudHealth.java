package com.cockroachlabs.crdb.continuum.cloud;

/**
 * Health probe for the Cockroach Continuum Cloud control plane.
 */
public final class CloudHealth {

    private CloudHealth() {
    }

    /**
     * Reports whether the Cloud control plane is reachable, by issuing a lightweight
     * {@link CloudClient#listClusters()}. Returns {@code true} on success and
     * {@code false} if the call throws. Use it for readiness probes.
     */
    public static boolean isReachable(CloudClient client) {
        try {
            client.listClusters();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
