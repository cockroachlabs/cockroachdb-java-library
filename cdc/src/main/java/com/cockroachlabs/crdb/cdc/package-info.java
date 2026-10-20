/**
 * CockroachDB Change Data Capture (CDC) integration.
 *
 * <p>{@link com.cockroachlabs.crdb.cdc.ChangefeedBuilder} renders
 * {@code CREATE CHANGEFEED ... INTO ... WITH ...} SQL;
 * {@link com.cockroachlabs.crdb.cdc.ChangefeedManager} creates and controls the
 * resulting jobs over a {@code DataSource}. Incoming webhook deliveries are
 * parsed by {@link com.cockroachlabs.crdb.cdc.CdcEventDecoder} into
 * {@link com.cockroachlabs.crdb.cdc.CdcEnvelope}/{@link com.cockroachlabs.crdb.cdc.CdcEvent}
 * records and routed to per-table handlers by
 * {@link com.cockroachlabs.crdb.cdc.ChangefeedHandlerRegistry}, wired to a JDK
 * HTTP server through {@link com.cockroachlabs.crdb.cdc.ChangefeedWebhookHandler}.
 *
 * <p>JSON (de)serialization uses Jackson, confined to this module.
 */
package com.cockroachlabs.crdb.cdc;
