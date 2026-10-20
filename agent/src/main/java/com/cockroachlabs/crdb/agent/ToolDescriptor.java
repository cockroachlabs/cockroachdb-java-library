package com.cockroachlabs.crdb.agent;

import com.fasterxml.jackson.databind.JsonNode;

import java.lang.reflect.Method;

/**
 * A single agent-callable tool discovered from an {@code @AgentTool} method.
 *
 * @param name         MCP tool name
 * @param description  human-readable description
 * @param destructive  whether the tool mutates state (hidden unless allowed)
 * @param inputSchema  JSON Schema for the tool's arguments
 * @param method       the backing method
 * @param target       the object the method is invoked on
 */
public record ToolDescriptor(
        String name,
        String description,
        boolean destructive,
        JsonNode inputSchema,
        Method method,
        Object target) {
}
