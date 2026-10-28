package com.enterprise.rag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reciprocal Rank Fusion (Cormack et al., 2009): {@code score(d) = Σ 1 / (k + rank_i(d))}.
 * <p>
 * RRF only uses ranks, so it merges cosine similarities and {@code ts_rank_cd} scores — which live on
 * incomparable scales — without any normalisation or weight tuning.
 */
public final class ReciprocalRankFusion {

    private ReciprocalRankFusion() {
    }

    public static List<RetrievedChunk> fuse(List<ScoredChunk> vectorResults, List<ScoredChunk> keywordResults,
            int k, int topK) {
        Map<Long, Accumulator> byChunk = new LinkedHashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            ScoredChunk c = vectorResults.get(i);
            Accumulator acc = byChunk.computeIfAbsent(c.chunkId(), id -> new Accumulator(c));
            acc.vectorRank = i + 1;
            acc.similarity = c.score();
            acc.score += 1.0 / (k + i + 1);
        }
        for (int i = 0; i < keywordResults.size(); i++) {
            ScoredChunk c = keywordResults.get(i);
            Accumulator acc = byChunk.computeIfAbsent(c.chunkId(), id -> new Accumulator(c));
            acc.keywordRank = i + 1;
            acc.score += 1.0 / (k + i + 1);
        }
        List<RetrievedChunk> fused = new ArrayList<>(byChunk.size());
        for (Accumulator acc : byChunk.values()) {
            fused.add(new RetrievedChunk(acc.chunk, acc.score, acc.vectorRank, acc.keywordRank, acc.similarity));
        }
        fused.sort(Comparator.comparingDouble(RetrievedChunk::fusedScore).reversed()
                .thenComparing(r -> r.vectorRank() == null ? Integer.MAX_VALUE : r.vectorRank()));
        return fused.size() > topK ? List.copyOf(fused.subList(0, topK)) : fused;
    }

    private static final class Accumulator {
        final ScoredChunk chunk;
        double score;
        Integer vectorRank;
        Integer keywordRank;
        Double similarity;

        Accumulator(ScoredChunk chunk) {
            this.chunk = chunk;
        }
    }
}
