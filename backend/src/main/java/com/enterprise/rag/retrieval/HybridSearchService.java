package com.enterprise.rag.retrieval;

import java.util.List;

import org.springframework.stereotype.Service;

import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.ingestion.EmbeddingService;
import com.enterprise.rag.security.AuthUser;

/**
 * Hybrid retrieval: semantic (pgvector) and keyword (Postgres FTS) candidates merged with RRF.
 * Semantic search catches paraphrases; keyword search catches exact terms such as policy codes,
 * product names and acronyms that embeddings tend to blur.
 */
@Service
public class HybridSearchService {

    private final ChunkRepository chunks;
    private final EmbeddingService embeddingService;
    private final RagProperties.Retrieval config;

    public HybridSearchService(ChunkRepository chunks, EmbeddingService embeddingService, RagProperties properties) {
        this.chunks = chunks;
        this.embeddingService = embeddingService;
        this.config = properties.retrieval();
    }

    public record Result(List<RetrievedChunk> chunks, int embeddingTokens, long latencyMs) {
    }

    public Result search(String query, AuthUser user) {
        return search(query, user, config.topK());
    }

    public Result search(String query, AuthUser user, int topK) {
        long start = System.nanoTime();
        EmbeddingService.Result embedding = embeddingService.embedQuery(query);

        List<ScoredChunk> semantic = chunks.vectorSearch(embedding.vectors().get(0), user, config.candidateK())
                .stream()
                .filter(c -> c.score() >= config.minSimilarity())
                .toList();
        List<ScoredChunk> keyword = KeywordQueryBuilder.toOrQuery(query)
                .map(q -> chunks.keywordSearch(q, user, config.candidateK()))
                .orElse(List.of());

        List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(semantic, keyword, config.rrfK(), topK);
        return new Result(fused, embedding.tokens(), (System.nanoTime() - start) / 1_000_000);
    }
}
