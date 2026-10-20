package com.cockroachlabs.crdb.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Marks the annotated method as an agent-callable tool. The {@code agent} module's
 * {@code ToolRegistry}/{@code CrdbAgent} reflect over these to expose an MCP-style
 * tool surface ({@code tools/list} + {@code tools/call}).
 */
@Documented
@Retention(RUNTIME)
@Target(METHOD)
public @interface AgentTool {

    /** The tool name exposed to agents (e.g. {@code describe_schema}). */
    String value();

    /**
     * Whether this tool performs a destructive/mutating action. Destructive tools
     * are hidden from listing and rejected on invocation unless the agent is
     * explicitly configured to allow them.
     */
    boolean destructive() default false;
}
