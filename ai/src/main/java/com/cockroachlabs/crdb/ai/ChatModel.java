package com.cockroachlabs.crdb.ai;

import java.util.List;

/**
 * A pluggable text-generation backend for RAG. Implementations wrap a specific
 * LLM (OpenAI, Anthropic, Ollama, a local model, ...). The AI module deliberately
 * takes no hard dependency on any LLM SDK — callers supply their own
 * implementation, or use {@link EchoChatModel} in tests.
 */
public interface ChatModel {

    /**
     * Answer {@code question} grounded in the supplied context passages.
     *
     * @param question the user's question
     * @param context  retrieved passages to ground the answer in
     * @return the generated answer
     */
    String generate(String question, List<String> context);
}
