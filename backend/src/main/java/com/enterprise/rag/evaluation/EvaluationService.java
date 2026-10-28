package com.enterprise.rag.evaluation;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import com.enterprise.rag.chat.AskResponse;
import com.enterprise.rag.chat.ChatService;
import com.enterprise.rag.chat.PromptBuilder;
import com.enterprise.rag.retrieval.RetrievedChunk;
import com.enterprise.rag.security.AuthUser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Runs the golden dataset through the real pipeline (retrieval + generation, bypassing the cache)
 * and records retrieval, answer-quality, safety, latency and cost metrics.
 */
@Service
public class EvaluationService {

    private static final Logger log = LoggerFactory.getLogger(EvaluationService.class);
    private static final String DATASET = "eval/golden-dataset.json";

    private final ChatService chatService;
    private final FaithfulnessJudge judge;
    private final EvalRunRepository runs;
    private final ObjectMapper objectMapper;

    public EvaluationService(ChatService chatService, FaithfulnessJudge judge, EvalRunRepository runs,
            ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.judge = judge;
        this.runs = runs;
        this.objectMapper = objectMapper;
    }

    public List<EvalCase> dataset() {
        try (InputStream in = new ClassPathResource(DATASET).getInputStream()) {
            return objectMapper.readValue(in, new TypeReference<List<EvalCase>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + DATASET, e);
        }
    }

    public EvalRun run(boolean useJudge) {
        List<EvalCase> cases = dataset();
        List<EvalCaseResult> results = new ArrayList<>(cases.size());
        for (EvalCase c : cases) {
            results.add(evaluate(c, useJudge));
        }
        EvalRun run = summarize(results, useJudge);
        log.info("Evaluation run {}: hit-rate={} mrr={} leakage={}", run.getId(), run.getHitRate(), run.getMrr(),
                run.getLeakageCount());
        return run;
    }

    EvalCaseResult evaluate(EvalCase c, boolean useJudge) {
        AuthUser evalUser = new AuthUser(null, "eval-" + c.asRole().name().toLowerCase(Locale.ROOT),
                Set.of(c.asRole()));
        ChatService.Generation generation = chatService.generate(c.question(), evalUser);
        AskResponse response = generation.response();
        List<RetrievedChunk> retrieved = generation.retrieved();
        List<String> titles = retrieved.stream().map(r -> r.chunk().documentTitle()).distinct().toList();

        int firstRelevantRank = 0;
        for (int i = 0; i < retrieved.size(); i++) {
            if (containsIgnoreCase(c.expectedDocuments(), retrieved.get(i).chunk().documentTitle())) {
                firstRelevantRank = i + 1;
                break;
            }
        }
        boolean hit = firstRelevantRank > 0;
        double reciprocalRank = hit ? 1.0 / firstRelevantRank : 0;
        double keywordRecall = keywordRecall(response.answer(), c.expectedKeywords());
        boolean refused = PromptBuilder.isRefusal(response.answer()) || !response.grounded();
        boolean leaked = titles.stream().anyMatch(t -> containsIgnoreCase(c.forbiddenDocuments(), t));

        Integer faithfulness = null;
        double cost = response.metrics().costUsd();
        if (useJudge && c.shouldAnswer() && response.grounded()) {
            FaithfulnessJudge.Verdict verdict = judge.judge(c.question(), response.answer(), retrieved);
            faithfulness = verdict.score();
            cost += verdict.costUsd();
        }

        boolean passed = !leaked && (c.shouldAnswer()
                ? hit && response.grounded() && keywordRecall >= 0.5
                : refused);
        return new EvalCaseResult(c.id(), c.question(), c.asRole(), c.shouldAnswer(), hit, reciprocalRank,
                keywordRecall, response.grounded(), refused, leaked, faithfulness, passed,
                response.metrics().latencyMs(), cost, response.answer(), titles);
    }

    private EvalRun summarize(List<EvalCaseResult> results, boolean useJudge) {
        Predicate<EvalCaseResult> answerable = EvalCaseResult::shouldAnswer;
        double hitRate = mean(results, answerable, r -> r.hit() ? 1 : 0);
        double mrr = mean(results, answerable, EvalCaseResult::reciprocalRank);
        double keywordRecall = mean(results, answerable, EvalCaseResult::keywordRecall);
        double citationRate = mean(results, answerable, r -> r.grounded() ? 1 : 0);
        double refusalAccuracy = mean(results, answerable.negate(), r -> r.refused() ? 1 : 0);
        int leakage = (int) results.stream().filter(EvalCaseResult::leaked).count();
        List<Integer> scores = results.stream().map(EvalCaseResult::faithfulness).filter(Objects::nonNull).toList();
        Double faithfulness = useJudge && !scores.isEmpty()
                ? scores.stream().mapToInt(Integer::intValue).average().orElse(0)
                : null;
        double avgLatency = results.stream().mapToLong(EvalCaseResult::latencyMs).average().orElse(0);
        double totalCost = results.stream().mapToDouble(EvalCaseResult::costUsd).sum();

        String json;
        try {
            json = objectMapper.writeValueAsString(results);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return runs.save(new EvalRun(results.size(), hitRate, mrr, keywordRecall, citationRate, refusalAccuracy,
                leakage, faithfulness, avgLatency, totalCost, json));
    }

    public List<EvalCaseResult> results(EvalRun run) {
        try {
            return objectMapper.readValue(run.getResultsJson(), new TypeReference<List<EvalCaseResult>>() {
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static double keywordRecall(String answer, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 1;
        }
        String text = answer == null ? "" : answer.toLowerCase(Locale.ROOT);
        long found = keywords.stream().filter(k -> text.contains(k.toLowerCase(Locale.ROOT))).count();
        return (double) found / keywords.size();
    }

    private static double mean(List<EvalCaseResult> results, Predicate<EvalCaseResult> filter,
            ToDoubleFunction<EvalCaseResult> metric) {
        return results.stream().filter(filter).mapToDouble(metric).average().orElse(0);
    }

    private static boolean containsIgnoreCase(List<String> values, String candidate) {
        return values.stream().anyMatch(v -> v.equalsIgnoreCase(candidate));
    }
}
