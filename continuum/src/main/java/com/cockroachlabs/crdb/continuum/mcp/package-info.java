/**
 * Cockroach Continuum MCP client surface: talks to an MCP server
 * ({@link com.cockroachlabs.crdb.continuum.mcp.SimulatedMcpClient simulated} and
 * {@link com.cockroachlabs.crdb.continuum.mcp.HttpMcpClient HTTP}) to expose
 * CockroachDB operations as agent-callable tools over the
 * {@link com.cockroachlabs.crdb.continuum.mcp.McpClient} abstraction.
 */
@NullMarked
package com.cockroachlabs.crdb.continuum.mcp;

import org.jspecify.annotations.NullMarked;
