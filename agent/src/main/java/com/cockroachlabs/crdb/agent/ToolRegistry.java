package com.cockroachlabs.crdb.agent;

import com.cockroachlabs.crdb.annotations.AgentTool;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Reflects over provider objects and builds {@link ToolDescriptor}s from their
 * public {@code @AgentTool} methods.
 */
public final class ToolRegistry {

    private final ObjectMapper mapper;
    private final Map<String, ToolDescriptor> tools = new LinkedHashMap<>();

    public ToolRegistry(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Register all {@code @AgentTool} methods on {@code provider}. */
    public ToolRegistry register(Object provider) {
        for (Method m : provider.getClass().getMethods()) {
            AgentTool tool = m.getAnnotation(AgentTool.class);
            if (tool == null) {
                continue;
            }
            m.setAccessible(true);
            String name = tool.value();
            tools.put(name, new ToolDescriptor(
                    name,
                    describe(name, m),
                    tool.destructive(),
                    JsonSchemaGenerator.forMethod(mapper, m),
                    m,
                    provider));
        }
        return this;
    }

    /** All registered tools (including destructive ones). */
    public Collection<ToolDescriptor> all() {
        return tools.values();
    }

    /** Look up a tool by name, or {@code null}. */
    public ToolDescriptor get(String name) {
        return tools.get(name);
    }

    private static String describe(String name, Method m) {
        StringJoiner sig = new StringJoiner(", ", name + "(", ")");
        for (Parameter p : m.getParameters()) {
            sig.add(p.getType().getSimpleName() + " " + p.getName());
        }
        return "Invokes " + sig + " -> " + m.getReturnType().getSimpleName();
    }
}
