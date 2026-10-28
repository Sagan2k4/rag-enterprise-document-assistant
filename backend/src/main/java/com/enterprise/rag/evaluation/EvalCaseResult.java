package com.enterprise.rag.evaluation;

import java.util.List;

import com.enterprise.rag.security.Role;

/**
 * @param hit            expected document appeared in the retrieved top-k (answerable cases only)
 * @param reciprocalRank 1 / rank of the first chunk from an expected document, 0 if none
 * @param keywordRecall  share of expected keywords present in the answer
 * @param refused        the assistant declined to answer
 * @param leaked         a forbidden document was retrieved — an RBAC failure
 * @param faithfulness   LLM-judge score 1–5, or null when the judge was not run
 */
public record EvalCaseResult(
        String id,
        String question,
        Role asRole,
        boolean shouldAnswer,
        boolean hit,
        double reciprocalRank,
        double keywordRecall,
        boolean grounded,
        boolean refused,
        boolean leaked,
        Integer faithfulness,
        boolean passed,
        long latencyMs,
        double costUsd,
        String answer,
        List<String> retrievedDocuments) {
}
