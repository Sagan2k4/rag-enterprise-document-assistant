package com.enterprise.rag.chat;

import java.util.List;

import com.enterprise.rag.retrieval.RetrievedChunk;

/**
 * @param answer   the model's answer, with inline [n] citation markers
 * @param sources  every passage that was put in the prompt; {@code cited} marks those the answer references
 * @param grounded true when the answer cites at least one source (i.e. is not a refusal)
 * @param cached   true when served from the Redis answer cache
 */
public record AskResponse(
        String answer,
        List<Source> sources,
        boolean grounded,
        boolean cached,
        String model,
        Metrics metrics) {

    public record Source(
            int number,
            boolean cited,
            long documentId,
            String documentTitle,
            String filename,
            Integer pageNumber,
            String section,
            String snippet,
            double fusedScore,
            Integer vectorRank,
            Integer keywordRank) {

        private static final int SNIPPET_LENGTH = 320;

        public static Source from(int number, RetrievedChunk r, boolean cited) {
            String content = r.chunk().content();
            String snippet = content.length() <= SNIPPET_LENGTH ? content
                    : content.substring(0, SNIPPET_LENGTH).strip() + "…";
            return new Source(number, cited, r.chunk().documentId(), r.chunk().documentTitle(),
                    r.chunk().filename(), r.chunk().pageNumber(), r.chunk().section(), snippet,
                    r.fusedScore(), r.vectorRank(), r.keywordRank());
        }
    }

    public record Metrics(
            long latencyMs,
            long retrievalMs,
            long generationMs,
            int promptTokens,
            int completionTokens,
            int embeddingTokens,
            double costUsd) {
    }

    /** A cache hit costs nothing and is as fast as the Redis round trip. */
    public AskResponse asCacheHit(long latencyMs) {
        return new AskResponse(answer, sources, grounded, true, model,
                new Metrics(latencyMs, 0, 0, 0, 0, 0, 0));
    }
}
