package com.enterprise.rag.ingestion;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import com.enterprise.rag.config.RagProperties;

/** Batches embedding calls and reports token usage for cost tracking. */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final TokenCounter tokenCounter;
    private final int batchSize;

    public EmbeddingService(EmbeddingModel embeddingModel, TokenCounter tokenCounter, RagProperties properties) {
        this.embeddingModel = embeddingModel;
        this.tokenCounter = tokenCounter;
        this.batchSize = properties.embedding().batchSize();
    }

    public record Result(List<float[]> vectors, int tokens) {
    }

    public Result embedAll(List<String> texts) {
        List<float[]> vectors = new ArrayList<>(texts.size());
        int tokens = 0;
        for (int from = 0; from < texts.size(); from += batchSize) {
            List<String> batch = texts.subList(from, Math.min(from + batchSize, texts.size()));
            EmbeddingResponse response = embeddingModel.embedForResponse(batch);
            response.getResults().stream().map(Embedding::getOutput).forEach(vectors::add);
            tokens += usedTokens(response, batch);
        }
        return new Result(vectors, tokens);
    }

    public Result embedQuery(String text) {
        return embedAll(List.of(text));
    }

    private int usedTokens(EmbeddingResponse response, List<String> batch) {
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        if (usage != null && usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
            return usage.getPromptTokens();
        }
        return batch.stream().mapToInt(tokenCounter::count).sum();
    }
}
