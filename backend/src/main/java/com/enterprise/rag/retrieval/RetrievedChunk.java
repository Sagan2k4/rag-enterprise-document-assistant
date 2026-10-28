package com.enterprise.rag.retrieval;

/**
 * A chunk after hybrid fusion.
 *
 * @param fusedScore  Reciprocal Rank Fusion score
 * @param vectorRank  1-based rank in semantic results, or null if absent
 * @param keywordRank 1-based rank in keyword results, or null if absent
 * @param similarity  cosine similarity to the query, or null if only found by keyword search
 */
public record RetrievedChunk(ScoredChunk chunk, double fusedScore, Integer vectorRank, Integer keywordRank,
        Double similarity) {
}
