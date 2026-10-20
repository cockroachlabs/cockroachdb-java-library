package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Routes the annotated method to a Cockroach vCluster (virtual cluster) for
 * workload isolation on shared capacity.
 *
 * <p>When a proxied method carrying this annotation opens a transaction, the
 * value is applied as {@code SET LOCAL application_name} to tag the session.
 * Combine with {@link Workload} to also set the transaction QoS.
 *
 * <p><b>Scope (v1):</b> workload tagging/QoS. Routing to a distinct vCluster
 * endpoint is platform-dependent and not performed here.
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface VirtualCluster {

    /** The virtual cluster name (e.g. {@code analytics}). */
    String value();
}
