package com.cockroachlabs.crdb.spi;

/**
 * A Cockroach Aegis recommendation: a suggested action derived from findings.
 *
 * @param id              stable recommendation identifier
 * @param title           short summary
 * @param detail          human-readable detail
 * @param suggestedAction the concrete action recommended
 */
public record Recommendation(String id, String title, String detail, String suggestedAction) {
}
