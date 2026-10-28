package com.enterprise.rag.observability;

import java.time.Instant;

import com.enterprise.rag.chat.AskResponse;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "query_logs")
public class QueryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String question;

    private String answer;

    private boolean grounded;

    @Column(name = "cache_hit")
    private boolean cacheHit;

    @Column(name = "sources_count")
    private int sourcesCount;

    @Column(name = "prompt_tokens")
    private int promptTokens;

    @Column(name = "completion_tokens")
    private int completionTokens;

    @Column(name = "embedding_tokens")
    private int embeddingTokens;

    @Column(name = "cost_usd")
    private double costUsd;

    @Column(name = "latency_ms")
    private long latencyMs;

    @Column(name = "retrieval_ms")
    private long retrievalMs;

    @Column(name = "generation_ms")
    private long generationMs;

    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected QueryLog() {
    }

    public static QueryLog of(String username, String question, AskResponse response) {
        QueryLog log = new QueryLog();
        log.username = username;
        log.question = question;
        log.answer = response.answer();
        log.grounded = response.grounded();
        log.cacheHit = response.cached();
        log.sourcesCount = response.sources().size();
        log.model = response.model();
        AskResponse.Metrics m = response.metrics();
        log.promptTokens = m.promptTokens();
        log.completionTokens = m.completionTokens();
        log.embeddingTokens = m.embeddingTokens();
        log.costUsd = m.costUsd();
        log.latencyMs = m.latencyMs();
        log.retrievalMs = m.retrievalMs();
        log.generationMs = m.generationMs();
        return log;
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getQuestion() {
        return question;
    }

    public boolean isGrounded() {
        return grounded;
    }

    public boolean isCacheHit() {
        return cacheHit;
    }

    public int getSourcesCount() {
        return sourcesCount;
    }

    public double getCostUsd() {
        return costUsd;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
