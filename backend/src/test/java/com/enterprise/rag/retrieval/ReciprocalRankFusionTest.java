package com.enterprise.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ReciprocalRankFusionTest {

    @Test
    void chunkFoundByBothRetrieversRanksFirst() {
        List<ScoredChunk> vector = List.of(chunk(1, 0.9), chunk(2, 0.8), chunk(3, 0.7));
        List<ScoredChunk> keyword = List.of(chunk(3, 4.0), chunk(4, 2.0));

        List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(vector, keyword, 60, 10);

        assertThat(fused.get(0).chunk().chunkId()).isEqualTo(3);
        assertThat(fused.get(0).vectorRank()).isEqualTo(3);
        assertThat(fused.get(0).keywordRank()).isEqualTo(1);
        assertThat(fused.get(0).fusedScore()).isEqualTo(1.0 / 63 + 1.0 / 61);
        assertThat(fused).extracting(r -> r.chunk().chunkId()).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
    }

    @Test
    void keepsSimilarityOnlyForSemanticHits() {
        List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(List.of(chunk(1, 0.42)), List.of(chunk(2, 1.0)), 60, 10);

        assertThat(fused).filteredOn(r -> r.chunk().chunkId() == 1).singleElement()
                .satisfies(r -> assertThat(r.similarity()).isEqualTo(0.42));
        assertThat(fused).filteredOn(r -> r.chunk().chunkId() == 2).singleElement()
                .satisfies(r -> assertThat(r.similarity()).isNull());
    }

    @Test
    void truncatesToTopK() {
        List<ScoredChunk> vector = List.of(chunk(1, 0.9), chunk(2, 0.8), chunk(3, 0.7), chunk(4, 0.6));

        assertThat(ReciprocalRankFusion.fuse(vector, List.of(), 60, 2))
                .extracting(r -> r.chunk().chunkId()).containsExactly(1L, 2L);
    }

    @Test
    void emptyInputsGiveEmptyResult() {
        assertThat(ReciprocalRankFusion.fuse(List.of(), List.of(), 60, 5)).isEmpty();
    }

    private static ScoredChunk chunk(long id, double score) {
        return new ScoredChunk(id, 100, "Doc", "doc.md", "MARKDOWN", (int) id, "content " + id, null, null, score);
    }
}
