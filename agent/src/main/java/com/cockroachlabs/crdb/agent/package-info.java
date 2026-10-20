/**
 * Agent / MCP surface: makes the database operationally legible and actionable to
 * agents by exposing {@code @AgentTool}-annotated methods as agent-callable tools.
 *
 * <p>{@link com.cockroachlabs.crdb.agent.ToolRegistry} reflects over provider
 * objects and generates typed JSON input schemas;
 * {@link com.cockroachlabs.crdb.agent.CrdbAgent} exposes an MCP-style
 * {@code tools/list} + {@code tools/call} surface with a least-privilege policy
 * (destructive tools hidden unless explicitly allowed).
 * {@link com.cockroachlabs.crdb.agent.CrdbOpsTools} ships a few safe built-in
 * tools.
 */
package com.cockroachlabs.crdb.agent;
