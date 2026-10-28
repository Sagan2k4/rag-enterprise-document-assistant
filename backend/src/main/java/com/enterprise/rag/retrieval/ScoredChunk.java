package com.enterprise.rag.retrieval;

/** A chunk returned by one retriever, with that retriever's raw score. */
public record ScoredChunk(
        long chunkId,
        long documentId,
        String documentTitle,
        String filename,
        String sourceType,
        int chunkIndex,
        String content,
        Integer pageNumber,
        String section,
        double score) {
}
