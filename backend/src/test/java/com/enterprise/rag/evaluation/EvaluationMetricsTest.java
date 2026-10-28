package com.enterprise.rag.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class EvaluationMetricsTest {

    @Test
    void keywordRecallIsCaseInsensitiveShareOfKeywordsFound() {
        assertThat(EvaluationService.keywordRecall("Up to 5 days, expiring march 31 [1].", List.of("5", "March 31")))
                .isEqualTo(1.0);
        assertThat(EvaluationService.keywordRecall("Up to 5 days [1].", List.of("5", "March 31"))).isEqualTo(0.5);
        assertThat(EvaluationService.keywordRecall(null, List.of("x"))).isZero();
        assertThat(EvaluationService.keywordRecall("anything", List.of())).isEqualTo(1.0);
    }

    @Test
    void goldenDatasetIsWellFormedAndCoversRbac() throws Exception {
        List<EvalCase> cases;
        try (InputStream in = new ClassPathResource("eval/golden-dataset.json").getInputStream()) {
            cases = new ObjectMapper().readValue(in, new TypeReference<>() {
            });
        }

        assertThat(cases).extracting(EvalCase::id).doesNotHaveDuplicates();
        assertThat(cases).allSatisfy(c -> {
            assertThat(c.question()).isNotBlank();
            assertThat(c.asRole()).isNotNull();
            if (c.shouldAnswer()) {
                assertThat(c.expectedDocuments()).isNotEmpty();
            }
        });
        assertThat(cases).anySatisfy(c -> assertThat(c.forbiddenDocuments()).isNotEmpty());
    }
}
