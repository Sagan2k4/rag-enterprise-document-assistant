package com.enterprise.rag.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerTest {

    @Test
    void detectsWrappedProviderQuotaError() {
        var cause = new RuntimeException("429 . You exceeded your current quota, please check your plan");
        assertThat(GlobalExceptionHandler.isModelRateLimited(new RuntimeException("Failed to generate content", cause)))
                .isTrue();
    }

    @Test
    void ignoresOrdinaryErrors() {
        assertThat(GlobalExceptionHandler.isModelRateLimited(new IllegalStateException("connection refused"))).isFalse();
        assertThat(GlobalExceptionHandler.isModelRateLimited(new RuntimeException("order 429 shipped"))).isFalse();
    }
}
