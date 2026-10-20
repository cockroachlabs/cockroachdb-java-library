package com.cockroachlabs.crdb.anomaly;

/**
 * Signals that a circuit breaker is OPEN and the call was rejected without being
 * attempted.
 *
 * @param openedFailures the consecutive failure count that tripped the breaker
 */
public record CircuitOpen(int openedFailures) implements CrdbAnomaly {

    @Override
    public String sqlState() {
        return null;
    }

    @Override
    public String message() {
        return "Circuit breaker OPEN after " + openedFailures + " consecutive failure(s)";
    }
}
