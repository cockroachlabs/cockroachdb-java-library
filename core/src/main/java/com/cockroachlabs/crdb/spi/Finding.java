package com.cockroachlabs.crdb.spi;

/**
 * A Cockroach Aegis finding: something the autonomous-ops agents observed about a
 * cluster.
 *
 * @param id       stable finding identifier
 * @param severity severity label (e.g. {@code info}, {@code warning}, {@code critical})
 * @param title    short summary
 * @param detail   human-readable detail
 */
public record Finding(String id, String severity, String title, String detail) {
}
