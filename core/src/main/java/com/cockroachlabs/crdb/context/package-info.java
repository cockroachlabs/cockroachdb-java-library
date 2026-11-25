/**
 * Virtual-thread-safe call context (region, vCluster, workload, follower-read
 * staleness) carried via Java's {@link java.lang.ScopedValue} — not
 * {@code ThreadLocal} — so it propagates correctly across structured concurrency
 * and virtual threads.
 */
@NullMarked
package com.cockroachlabs.crdb.context;

import org.jspecify.annotations.NullMarked;
