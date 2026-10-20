package com.cockroachlabs.crdb.cdc;

import java.util.List;

/**
 * Processes a batch of decoded CDC events from a single webhook delivery.
 */
@FunctionalInterface
public interface CdcEventHandler {

    /** Handle the events for one table from one delivery. */
    void handle(List<CdcEvent> events);
}
