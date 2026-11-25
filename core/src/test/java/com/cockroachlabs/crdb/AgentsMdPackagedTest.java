package com.cockroachlabs.crdb;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/**
 * Teeth-check that the consumer-facing agent guide is packaged into the core
 * jar at {@code META-INF/cockroachdb-java-library/AGENTS.md}. The resource is
 * placed there by the {@code processResources} bake step in
 * {@code core/build.gradle}; this test fails if that step is removed or the
 * source file disappears.
 */
class AgentsMdPackagedTest {

    @Test
    void agentsMdIsOnTheClasspath() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/META-INF/cockroachdb-java-library/AGENTS.md")) {
            assertNotNull(in, "META-INF/cockroachdb-java-library/AGENTS.md must be packaged into the core jar");
            String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(body.length() > 100, "META-INF/AGENTS.md must be non-empty");
            assertTrue(body.contains("cockroachdb-java-library"),
                    "META-INF/AGENTS.md must be the consumer agent guide");
        }
    }
}
