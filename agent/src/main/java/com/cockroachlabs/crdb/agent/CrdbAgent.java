package com.cockroachlabs.crdb.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal, dependency-light MCP-style facade over {@code @AgentTool} methods.
 *
 * <p>Build it from one or more provider objects with {@link #from(Object...)},
 * then use {@link #listTools()} (MCP {@code tools/list} shape) and
 * {@link #invoke(String, String)} (MCP {@code tools/call} shape). Least-privilege
 * by default: only {@code @AgentTool} methods are exposed, and destructive tools
 * are hidden and rejected unless {@link #allowDestructive(boolean)} is set.
 *
 * <p>A network transport (stdio/HTTP) is intentionally out of scope for this
 * increment; {@code listTools}/{@code invoke} return JSON strings a transport can
 * relay.
 */
public final class CrdbAgent {

    private final ObjectMapper mapper;
    private final ToolRegistry registry;
    private boolean allowDestructive;

    private CrdbAgent(ObjectMapper mapper, ToolRegistry registry) {
        this.mapper = mapper;
        this.registry = registry;
    }

    /** Build an agent exposing the {@code @AgentTool} methods of the providers. */
    public static CrdbAgent from(Object... providers) {
        ObjectMapper mapper = new ObjectMapper();
        ToolRegistry registry = new ToolRegistry(mapper);
        for (Object p : providers) {
            registry.register(p);
        }
        return new CrdbAgent(mapper, registry);
    }

    /** Allow (or disallow) destructive tools to be listed and invoked. */
    public CrdbAgent allowDestructive(boolean allow) {
        this.allowDestructive = allow;
        return this;
    }

    /** The tools currently visible under the least-privilege policy. */
    public List<ToolDescriptor> tools() {
        List<ToolDescriptor> visible = new ArrayList<>();
        for (ToolDescriptor t : registry.all()) {
            if (isVisible(t)) {
                visible.add(t);
            }
        }
        return visible;
    }

    /** A visible tool by name, or {@code null}. */
    public ToolDescriptor tool(String name) {
        ToolDescriptor t = registry.get(name);
        return (t != null && isVisible(t)) ? t : null;
    }

    /** MCP {@code tools/list} response as a JSON string. */
    public String listTools() {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode arr = root.putArray("tools");
        for (ToolDescriptor t : tools()) {
            ObjectNode node = arr.addObject();
            node.put("name", t.name());
            node.put("description", t.description());
            node.set("inputSchema", t.inputSchema());
        }
        return write(root);
    }

    /**
     * MCP {@code tools/call} for {@code name} with JSON arguments, returning the
     * result as an MCP tool-result JSON string.
     *
     * @throws IllegalArgumentException if the tool is unknown or not permitted
     */
    public String invoke(String name, String argsJson) {
        ToolDescriptor tool = tool(name);
        if (tool == null) {
            throw new IllegalArgumentException("Unknown or not permitted tool: " + name);
        }
        try {
            Object[] args = bindArgs(tool.method(), argsJson);
            Object result = tool.method().invoke(tool.target(), args);
            return toolResult(result, false);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return toolResult(cause.getMessage(), true);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            return toolResult(e.getMessage(), true);
        }
    }

    private boolean isVisible(ToolDescriptor t) {
        return !t.destructive() || allowDestructive;
    }

    private Object[] bindArgs(Method method, String argsJson) {
        JsonNode root;
        try {
            root = (argsJson == null || argsJson.isBlank())
                    ? mapper.createObjectNode()
                    : mapper.readTree(argsJson);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON arguments: " + e.getMessage(), e);
        }
        Parameter[] params = method.getParameters();
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            JsonNode node = root.get(p.getName());
            if (node == null || node.isNull()) {
                if (p.getType().isPrimitive()) {
                    throw new IllegalArgumentException("Missing required argument: " + p.getName());
                }
                args[i] = null;
            } else {
                args[i] = mapper.convertValue(node, p.getType());
            }
        }
        return args;
    }

    private String toolResult(Object result, boolean isError) {
        ObjectNode root = mapper.createObjectNode();
        ArrayNode content = root.putArray("content");
        ObjectNode item = content.addObject();
        item.put("type", "text");
        item.put("text", isError ? String.valueOf(result) : write(result));
        root.put("isError", isError);
        return write(root);
    }

    private String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }
}
