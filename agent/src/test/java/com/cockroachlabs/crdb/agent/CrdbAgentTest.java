package com.cockroachlabs.crdb.agent;

import com.cockroachlabs.crdb.annotations.AgentTool;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrdbAgentTest {

    /** Provider with a safe echo tool that takes typed arguments. */
    public static final class EchoTools {
        @AgentTool("echo")
        public String echo(String msg, int n) {
            return msg + ":" + n;
        }
    }

    /** Provider with a destructive tool. */
    public static final class DangerTools {
        @AgentTool(value = "drop_it", destructive = true)
        public String dropIt() {
            return "dropped";
        }
    }

    @Test
    void listsBuiltInToolsWithSchemas() {
        CrdbAgent agent = CrdbAgent.from(new CrdbOpsTools());

        Set<String> names = agent.tools().stream()
                .map(ToolDescriptor::name).collect(Collectors.toSet());
        assertTrue(names.containsAll(Set.of("health", "retry_stats", "storage_summary")));

        String json = agent.listTools();
        assertTrue(json.contains("\"tools\""));
        assertTrue(json.contains("inputSchema"));

        ToolDescriptor health = agent.tool("health");
        assertNotNull(health);
        assertEquals("object", health.inputSchema().get("type").asText());
    }

    @Test
    void schemaReflectsTypedParameters() {
        CrdbAgent agent = CrdbAgent.from(new EchoTools());
        ToolDescriptor echo = agent.tool("echo");
        assertNotNull(echo);
        var props = echo.inputSchema().get("properties");
        assertEquals("string", props.get("msg").get("type").asText());
        assertEquals("integer", props.get("n").get("type").asText());
    }

    @Test
    void invokeRunsToolAndReturnsResult() {
        CrdbAgent agent = CrdbAgent.from(new CrdbOpsTools());

        String health = agent.invoke("health", "{}");
        assertTrue(health.contains("ok"));
        assertTrue(health.contains("\"isError\":false"));

        String stats = agent.invoke("retry_stats", "{}");
        assertTrue(stats.contains("retryAttempts"));
    }

    @Test
    void invokeBindsTypedArguments() {
        CrdbAgent agent = CrdbAgent.from(new EchoTools());
        String result = agent.invoke("echo", "{\"msg\":\"hi\",\"n\":3}");
        assertTrue(result.contains("hi:3"));
    }

    @Test
    void destructiveToolHiddenUnlessAllowed() {
        CrdbAgent locked = CrdbAgent.from(new DangerTools());
        List<String> names = locked.tools().stream().map(ToolDescriptor::name).toList();
        assertFalse(names.contains("drop_it"), "destructive tool hidden by default");
        assertThrows(IllegalArgumentException.class, () -> locked.invoke("drop_it", "{}"));

        CrdbAgent allowed = CrdbAgent.from(new DangerTools()).allowDestructive(true);
        assertTrue(allowed.tools().stream().anyMatch(t -> t.name().equals("drop_it")));
        String result = allowed.invoke("drop_it", "{}");
        assertTrue(result.contains("dropped"));
    }
}
