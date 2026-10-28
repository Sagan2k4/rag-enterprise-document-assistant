package com.enterprise.rag.evaluation;

import java.time.Instant;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.enterprise.rag.common.ApiException;

@RestController
@RequestMapping("/api/eval")
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final EvalRunRepository runs;

    public EvaluationController(EvaluationService evaluationService, EvalRunRepository runs) {
        this.evaluationService = evaluationService;
        this.runs = runs;
    }

    public record RunSummary(Long id, Instant createdAt, int caseCount, double hitRate, double mrr,
            double keywordRecall, double citationRate, double refusalAccuracy, int leakageCount, Double faithfulness,
            double avgLatencyMs, double totalCostUsd) {

        static RunSummary from(EvalRun r) {
            return new RunSummary(r.getId(), r.getCreatedAt(), r.getCaseCount(), r.getHitRate(), r.getMrr(),
                    r.getKeywordRecall(), r.getCitationRate(), r.getRefusalAccuracy(), r.getLeakageCount(),
                    r.getFaithfulness(), r.getAvgLatencyMs(), r.getTotalCostUsd());
        }
    }

    public record RunDetail(RunSummary summary, List<EvalCaseResult> cases) {
    }

    @GetMapping("/dataset")
    public List<EvalCase> dataset() {
        return evaluationService.dataset();
    }

    /** Synchronous: the bundled dataset runs in well under a minute. */
    @PostMapping("/runs")
    public RunDetail run(@RequestParam(defaultValue = "false") boolean judge) {
        EvalRun run = evaluationService.run(judge);
        return new RunDetail(RunSummary.from(run), evaluationService.results(run));
    }

    @GetMapping("/runs")
    public List<RunSummary> list() {
        return runs.findTop20ByOrderByCreatedAtDesc().stream().map(RunSummary::from).toList();
    }

    @GetMapping("/runs/{id}")
    public RunDetail get(@PathVariable Long id) {
        EvalRun run = runs.findById(id).orElseThrow(() -> ApiException.notFound("Evaluation run not found"));
        return new RunDetail(RunSummary.from(run), evaluationService.results(run));
    }
}
