package com.cockroachlabs.crdb.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A minimal Retrieval-Augmented Generation helper: retrieve the most similar
 * documents from a {@link VectorStore}, then hand their content to a
 * {@link ChatModel} to generate a grounded answer.
 *
 * <pre>{@code
 * var rag = new RagTemplate(vectorStore, new EchoChatModel());
 * String answer = rag.answer("How does CockroachDB retry?", 4);
 * }</pre>
 */
public final class RagTemplate {

    private final VectorStore store;
    private final ChatModel chatModel;

    public RagTemplate(VectorStore store, ChatModel chatModel) {
        this.store = Objects.requireNonNull(store, "store");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
    }

    /** Retrieve the {@code topK} most similar documents to {@code question}. */
    public List<VectorSearchResult> retrieve(String question, int topK) {
        return store.similaritySearch(question, topK);
    }

    /** Retrieve with an optional SQL metadata filter. */
    public List<VectorSearchResult> retrieve(String question, int topK, String filter) {
        return store.similaritySearch(question, topK, filter);
    }

    /** Retrieve context and generate a grounded answer. */
    public String answer(String question, int topK) {
        return answer(question, topK, null);
    }

    /** Retrieve context (optionally filtered) and generate a grounded answer. */
    public String answer(String question, int topK, String filter) {
        List<VectorSearchResult> hits = retrieve(question, topK, filter);
        List<String> context = new ArrayList<>(hits.size());
        for (VectorSearchResult hit : hits) {
            context.add(hit.content());
        }
        return chatModel.generate(question, context);
    }
}
