package com.enterprise.rag.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;

import com.enterprise.rag.config.RagProperties;

class CostCalculatorTest {

    private final CostCalculator calculator = new CostCalculator(new RagProperties.Pricing(0.15, 0.60, 0.02));

    @Test
    void chatCostUsesSeparateInputAndOutputPrices() {
        // 2,000 prompt tokens * $0.15/M + 500 completion tokens * $0.60/M
        assertThat(calculator.chatCost(2_000, 500)).isCloseTo(0.0006, within(1e-12));
    }

    @Test
    void totalIncludesEmbeddingTokens() {
        assertThat(calculator.totalCost(2_000, 500, 1_000_000)).isCloseTo(0.0206, within(1e-12));
    }
}
