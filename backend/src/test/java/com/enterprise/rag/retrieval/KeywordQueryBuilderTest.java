package com.enterprise.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KeywordQueryBuilderTest {

    @Test
    void orsDistinctTerms() {
        assertThat(KeywordQueryBuilder.toOrQuery("What is the SEV1 response, the SEV1 SLA?"))
                .contains("what | is | the | sev1 | response | sla");
    }

    @Test
    void stripsTsqueryOperatorsSoInputCannotInject() {
        assertThat(KeywordQueryBuilder.toOrQuery("leave & !policy | (x:*) <-> drop"))
                .contains("leave | policy | drop");
    }

    @Test
    void emptyWhenNoUsableTerms() {
        assertThat(KeywordQueryBuilder.toOrQuery("?! a")).isEmpty();
        assertThat(KeywordQueryBuilder.toOrQuery(null)).isEmpty();
    }
}
