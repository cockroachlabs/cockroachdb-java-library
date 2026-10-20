package com.cockroachlabs.crdb.circuit;

/** Circuit breaker states. */
public enum State {
    /** Calls flow normally; failures are counted. */
    CLOSED,
    /** Calls are rejected immediately until the reset timeout elapses. */
    OPEN,
    /** A single trial call is allowed to probe recovery. */
    HALF_OPEN
}
