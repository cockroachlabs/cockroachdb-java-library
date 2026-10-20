package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Tags the annotated method's workload priority/class for a Cockroach vCluster.
 *
 * <p>When a proxied method carrying this annotation opens a transaction, the
 * priority is mapped to CockroachDB's {@code default_transaction_quality_of_service}
 * session setting (see {@link Priority#qos()}).
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface Workload {

    /** Workload priority class. */
    Priority value();

    /** Relative importance of a workload sharing a virtual cluster. */
    enum Priority {
        /** Maps to QoS {@code critical}. */
        HIGH("critical"),
        /** Maps to QoS {@code regular} (CockroachDB default). */
        NORMAL("regular"),
        /** Maps to QoS {@code background}. */
        LOW("background");

        private final String qos;

        Priority(String qos) {
            this.qos = qos;
        }

        /**
         * The CockroachDB {@code default_transaction_quality_of_service} value for
         * this priority: one of {@code critical}, {@code regular}, {@code background}.
         */
        public String qos() {
            return qos;
        }
    }
}
