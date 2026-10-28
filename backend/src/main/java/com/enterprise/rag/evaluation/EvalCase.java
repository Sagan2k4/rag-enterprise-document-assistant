package com.enterprise.rag.evaluation;

import java.util.List;

import com.enterprise.rag.security.Role;

/**
 * One golden-dataset question.
 *
 * @param asRole             the role the question is asked as (exercises RBAC)
 * @param expectedDocuments  titles that should be retrieved for an answerable question
 * @param expectedKeywords   facts that a correct answer must mention
 * @param shouldAnswer       false when the user must NOT get an answer (out of scope or forbidden)
 * @param forbiddenDocuments titles that must never be retrieved for this role
 */
public record EvalCase(
        String id,
        String question,
        Role asRole,
        List<String> expectedDocuments,
        List<String> expectedKeywords,
        boolean shouldAnswer,
        List<String> forbiddenDocuments) {

    public EvalCase {
        expectedDocuments = expectedDocuments == null ? List.of() : expectedDocuments;
        expectedKeywords = expectedKeywords == null ? List.of() : expectedKeywords;
        forbiddenDocuments = forbiddenDocuments == null ? List.of() : forbiddenDocuments;
    }
}
