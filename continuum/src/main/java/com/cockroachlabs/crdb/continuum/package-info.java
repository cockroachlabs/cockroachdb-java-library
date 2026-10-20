/**
 * Cockroach Continuum surfaces: <b>Plenum</b> (usage-based storage insights),
 * <b>vCluster</b> (workload tagging &amp; QoS), and <b>Aegis</b>
 * (autonomous-ops findings + exhaust).
 *
 * <p>Plenum {@link com.cockroachlabs.crdb.continuum.plenum.StorageInsightsImpl}
 * and the Aegis {@link com.cockroachlabs.crdb.continuum.aegis.SimulatedAegisClient}
 * / {@link com.cockroachlabs.crdb.continuum.aegis.AegisExhaust} are implemented
 * and registered as {@code core} SPI providers via {@code META-INF/services}, so
 * they are reachable through {@code Crdb.storage(...)} and {@code Crdb.aegis(...)}.
 * vCluster tagging is applied by the core transaction template from context set
 * via {@code @VirtualCluster}/{@code @Workload} or {@code Crdb.vcluster(...)}.
 */
package com.cockroachlabs.crdb.continuum;
