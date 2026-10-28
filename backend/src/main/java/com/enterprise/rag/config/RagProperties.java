package com.enterprise.rag.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunables for the RAG pipeline, bound from the {@code rag.*} namespace.
 */
@ConfigurationProperties(prefix = "rag")
public record RagProperties(
        Jwt jwt,
        Chunking chunking,
        Retrieval retrieval,
        Embedding embedding,
        Cache cache,
        Pricing pricing,
        Seed seed,
        Cors cors) {

    public record Jwt(String secret, long expirationMinutes) {
    }

    /** Chunk size and overlap, measured in cl100k tokens. */
    public record Chunking(int maxTokens, int overlapTokens) {
    }

    /**
     * @param topK          chunks passed to the LLM
     * @param candidateK    candidates fetched from each retriever before fusion
     * @param rrfK          Reciprocal Rank Fusion damping constant (60 is the value from the original paper)
     * @param minSimilarity cosine similarity floor for semantic candidates
     */
    public record Retrieval(int topK, int candidateK, int rrfK, double minSimilarity) {
    }

    public record Embedding(int batchSize) {
    }

    public record Cache(boolean enabled, Duration ttl) {
    }

    /** USD per one million tokens. */
    public record Pricing(double chatInputPerMillion, double chatOutputPerMillion, double embeddingPerMillion) {
    }

    public record Seed(boolean users, boolean sampleDocs) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
