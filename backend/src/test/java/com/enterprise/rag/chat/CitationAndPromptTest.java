package com.enterprise.rag.chat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.enterprise.rag.retrieval.RetrievedChunk;
import com.enterprise.rag.retrieval.ScoredChunk;

class CitationAndPromptTest {

    @Test
    void extractsSingleAdjacentAndCommaSeparatedMarkers() {
        String answer = "You get 20 days [2]. Carry-over is 5 days [1][3], expiring March 31 [3, 4].";

        assertThat(CitationExtractor.extract(answer, 4)).containsExactly(2, 1, 3, 4);
    }

    @Test
    void dropsMarkersThatPointAtNonExistentPassages() {
        assertThat(CitationExtractor.extract("See [0] and [7] and [2].", 3)).containsExactly(2);
    }

    @Test
    void noMarkersMeansNoCitations() {
        assertThat(CitationExtractor.extract(PromptBuilder.NO_ANSWER, 5)).isEmpty();
        assertThat(CitationExtractor.extract(null, 5)).isEmpty();
    }

    @Test
    void promptNumbersPassagesWithPageAndSection() {
        List<RetrievedChunk> chunks = List.of(
                retrieved(new ScoredChunk(1, 10, "Employee Handbook", "h.pdf", "PDF", 0, "20 days leave", 2, null, 0.8)),
                retrieved(new ScoredChunk(2, 11, "IT Security Policy", "s.md", "MARKDOWN", 0, "14 chars", null,
                        "Passwords", 0.7)));

        String prompt = PromptBuilder.userMessage("How much leave?", chunks);

        assertThat(prompt)
                .contains("[1] Employee Handbook — page 2\n20 days leave")
                .contains("[2] IT Security Policy — section \"Passwords\"\n14 chars")
                .startsWith("<context>")
                .endsWith("Question: How much leave?");
    }

    @Test
    void systemPromptContainsExactRefusalSentence() {
        assertThat(PromptBuilder.SYSTEM_PROMPT).contains(PromptBuilder.NO_ANSWER);
        assertThat(PromptBuilder.isRefusal(PromptBuilder.NO_ANSWER)).isTrue();
        assertThat(PromptBuilder.isRefusal("You get 20 days [1].")).isFalse();
    }

    @Test
    void cacheKeyNormalisationIgnoresCaseAndPunctuation() {
        assertThat(AnswerCache.normalize("  How many  LEAVE days?! ")).isEqualTo(AnswerCache.normalize("how many leave days"));
    }

    private static RetrievedChunk retrieved(ScoredChunk c) {
        return new RetrievedChunk(c, 0.03, 1, null, c.score());
    }
}
