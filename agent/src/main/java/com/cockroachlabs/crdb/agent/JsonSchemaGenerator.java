package com.cockroachlabs.crdb.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;

/**
 * Generates a pragmatic JSON Schema for a tool method's arguments from its
 * parameter types and names. Supports {@code String}, numeric, boolean, and
 * simple record parameters; anything else is described as a generic object.
 */
final class JsonSchemaGenerator {

    private JsonSchemaGenerator() {
    }

    /** Build an {@code object} schema describing {@code method}'s parameters. */
    static ObjectNode forMethod(ObjectMapper mapper, Method method) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode props = schema.putObject("properties");
        var required = schema.putArray("required");
        for (Parameter p : method.getParameters()) {
            props.set(p.getName(), typeSchema(mapper, p.getType()));
            required.add(p.getName());
        }
        return schema;
    }

    private static ObjectNode typeSchema(ObjectMapper mapper, Class<?> type) {
        ObjectNode node = mapper.createObjectNode();
        if (type == String.class || type == char.class || type == Character.class) {
            node.put("type", "string");
        } else if (isInteger(type)) {
            node.put("type", "integer");
        } else if (isDecimal(type)) {
            node.put("type", "number");
        } else if (type == boolean.class || type == Boolean.class) {
            node.put("type", "boolean");
        } else if (type.isRecord()) {
            node.put("type", "object");
            ObjectNode props = node.putObject("properties");
            for (RecordComponent rc : type.getRecordComponents()) {
                props.set(rc.getName(), typeSchema(mapper, rc.getType()));
            }
        } else {
            node.put("type", "object");
        }
        return node;
    }

    private static boolean isInteger(Class<?> t) {
        return t == int.class || t == Integer.class
                || t == long.class || t == Long.class
                || t == short.class || t == Short.class
                || t == byte.class || t == Byte.class;
    }

    private static boolean isDecimal(Class<?> t) {
        return t == double.class || t == Double.class
                || t == float.class || t == Float.class;
    }
}
