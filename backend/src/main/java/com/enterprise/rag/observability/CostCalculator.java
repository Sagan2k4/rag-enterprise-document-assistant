package com.enterprise.rag.observability;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.enterprise.rag.config.RagProperties;

/** Converts token usage into USD using the configured per-million-token prices. */
@Component
public class CostCalculator {

    private static final double MILLION = 1_000_000d;

    private final RagProperties.Pricing pricing;

    @Autowired
    public CostCalculator(RagProperties properties) {
        this(properties.pricing());
    }

    public CostCalculator(RagProperties.Pricing pricing) {
        this.pricing = pricing;
    }

    public double chatCost(int promptTokens, int completionTokens) {
        return promptTokens * pricing.chatInputPerMillion() / MILLION
                + completionTokens * pricing.chatOutputPerMillion() / MILLION;
    }

    public double embeddingCost(int tokens) {
        return tokens * pricing.embeddingPerMillion() / MILLION;
    }

    public double totalCost(int promptTokens, int completionTokens, int embeddingTokens) {
        return chatCost(promptTokens, completionTokens) + embeddingCost(embeddingTokens);
    }
}
