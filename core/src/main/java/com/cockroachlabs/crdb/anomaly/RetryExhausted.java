package com.cockroachlabs.crdb.anomaly;

/**
 * Signals that the retry engine gave up after exhausting all attempts.
 *
 * @param attempts how many attempts were made before giving up
 * @param last     the anomaly observed on the final attempt
 */
public record RetryExhausted(int attempts, CrdbAnomaly last) implements CrdbAnomaly {

    @Override
    public String sqlState() {
        return last == null ? null : last.sqlState();
    }

    @Override
    public String message() {
        return "Retry exhausted after " + attempts + " attempt(s)"
                + (last == null ? "" : "; last: " + last.message());
    }
}
