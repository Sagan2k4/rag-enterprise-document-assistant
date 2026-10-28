package com.enterprise.rag.evaluation;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "eval_runs")
public class EvalRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_count")
    private int caseCount;

    @Column(name = "hit_rate")
    private double hitRate;

    private double mrr;

    @Column(name = "keyword_recall")
    private double keywordRecall;

    @Column(name = "citation_rate")
    private double citationRate;

    @Column(name = "refusal_accuracy")
    private double refusalAccuracy;

    @Column(name = "leakage_count")
    private int leakageCount;

    private Double faithfulness;

    @Column(name = "avg_latency_ms")
    private double avgLatencyMs;

    @Column(name = "total_cost_usd")
    private double totalCostUsd;

    @Column(name = "results_json", nullable = false)
    private String resultsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected EvalRun() {
    }

    public EvalRun(int caseCount, double hitRate, double mrr, double keywordRecall, double citationRate,
            double refusalAccuracy, int leakageCount, Double faithfulness, double avgLatencyMs, double totalCostUsd,
            String resultsJson) {
        this.caseCount = caseCount;
        this.hitRate = hitRate;
        this.mrr = mrr;
        this.keywordRecall = keywordRecall;
        this.citationRate = citationRate;
        this.refusalAccuracy = refusalAccuracy;
        this.leakageCount = leakageCount;
        this.faithfulness = faithfulness;
        this.avgLatencyMs = avgLatencyMs;
        this.totalCostUsd = totalCostUsd;
        this.resultsJson = resultsJson;
    }

    public Long getId() {
        return id;
    }

    public int getCaseCount() {
        return caseCount;
    }

    public double getHitRate() {
        return hitRate;
    }

    public double getMrr() {
        return mrr;
    }

    public double getKeywordRecall() {
        return keywordRecall;
    }

    public double getCitationRate() {
        return citationRate;
    }

    public double getRefusalAccuracy() {
        return refusalAccuracy;
    }

    public int getLeakageCount() {
        return leakageCount;
    }

    public Double getFaithfulness() {
        return faithfulness;
    }

    public double getAvgLatencyMs() {
        return avgLatencyMs;
    }

    public double getTotalCostUsd() {
        return totalCostUsd;
    }

    public String getResultsJson() {
        return resultsJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
