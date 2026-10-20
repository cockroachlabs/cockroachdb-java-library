package com.cockroachlabs.crdb.ai;

import java.util.List;

/**
 * A trivial, deterministic {@link ChatModel} that echoes the question and the
 * retrieved context instead of calling a real LLM. Useful for tests and for
 * validating retrieval wiring without an API key or network access.
 */
public final class EchoChatModel implements ChatModel {

    @Override
    public String generate(String question, List<String> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("Q: ").append(question).append('\n');
        sb.append("Context (").append(context.size()).append(" passages):");
        for (String passage : context) {
            sb.append("\n- ").append(passage);
        }
        return sb.toString();
    }
}
