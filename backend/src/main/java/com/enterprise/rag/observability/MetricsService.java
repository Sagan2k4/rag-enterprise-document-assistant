package com.enterprise.rag.observability;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Aggregates query logs into cost / latency / quality metrics for the admin dashboard. */
@Service
public class MetricsService {

    private static final String SUMMARY_SQL = """
            SELECT count(*)                                                         AS total,
                   coalesce(avg(latency_ms), 0)                                     AS avg_latency,
                   coalesce(percentile_cont(0.5) WITHIN GROUP (ORDER BY latency_ms), 0)  AS p50,
                   coalesce(percentile_cont(0.95) WITHIN GROUP (ORDER BY latency_ms), 0) AS p95,
                   coalesce(sum(cost_usd), 0)                                       AS cost,
                   coalesce(avg(CASE WHEN cache_hit THEN 1.0 ELSE 0.0 END), 0)      AS cache_rate,
                   coalesce(avg(CASE WHEN grounded THEN 1.0 ELSE 0.0 END), 0)       AS grounded_rate,
                   coalesce(sum(prompt_tokens), 0)                                  AS prompt_tokens,
                   coalesce(sum(completion_tokens), 0)                              AS completion_tokens
            FROM query_logs
            WHERE created_at >= ?""";

    private static final String DAILY_SQL = """
            SELECT (created_at AT TIME ZONE 'UTC')::date AS day,
                   count(*)                              AS queries,
                   coalesce(sum(cost_usd), 0)            AS cost,
                   coalesce(avg(latency_ms), 0)          AS avg_latency
            FROM query_logs
            WHERE created_at >= ?
            GROUP BY 1
            ORDER BY 1""";

    private static final String CORPUS_SQL = """
            SELECT count(*)                                         AS documents,
                   count(*) FILTER (WHERE status = 'READY')         AS ready,
                   count(*) FILTER (WHERE status = 'FAILED')        AS failed,
                   coalesce(sum(chunk_count), 0)                    AS chunks,
                   coalesce(sum(token_count), 0)                    AS tokens,
                   coalesce(sum(embedding_cost_usd), 0)             AS embedding_cost
            FROM documents""";

    private final JdbcTemplate jdbc;
    private final QueryLogRepository queryLogs;

    public MetricsService(JdbcTemplate jdbc, QueryLogRepository queryLogs) {
        this.jdbc = jdbc;
        this.queryLogs = queryLogs;
    }

    public record Summary(long totalQueries, double avgLatencyMs, double p50LatencyMs, double p95LatencyMs,
            double totalCostUsd, double avgCostPerQueryUsd, double cacheHitRate, double groundedRate,
            long promptTokens, long completionTokens) {
    }

    public record DailyPoint(LocalDate day, long queries, double costUsd, double avgLatencyMs) {
    }

    public record Corpus(long documents, long ready, long failed, long chunks, long tokens,
            double embeddingCostUsd) {
    }

    public record RecentQuery(Long id, String username, String question, boolean grounded, boolean cacheHit,
            int sources, long latencyMs, double costUsd, Instant createdAt) {
    }

    public record Dashboard(int days, Summary summary, List<DailyPoint> daily, Corpus corpus,
            List<RecentQuery> recent) {
    }

    public Dashboard dashboard(int days) {
        Timestamp since = Timestamp.from(Instant.now().minus(days, ChronoUnit.DAYS));

        Summary summary = jdbc.queryForObject(SUMMARY_SQL, (rs, i) -> {
            long total = rs.getLong("total");
            double cost = rs.getDouble("cost");
            return new Summary(total, rs.getDouble("avg_latency"), rs.getDouble("p50"), rs.getDouble("p95"),
                    cost, total == 0 ? 0 : cost / total, rs.getDouble("cache_rate"), rs.getDouble("grounded_rate"),
                    rs.getLong("prompt_tokens"), rs.getLong("completion_tokens"));
        }, since);

        List<DailyPoint> daily = jdbc.query(DAILY_SQL, (rs, i) -> new DailyPoint(
                rs.getObject("day", LocalDate.class), rs.getLong("queries"), rs.getDouble("cost"),
                rs.getDouble("avg_latency")), since);

        Corpus corpus = jdbc.queryForObject(CORPUS_SQL, (rs, i) -> new Corpus(rs.getLong("documents"),
                rs.getLong("ready"), rs.getLong("failed"), rs.getLong("chunks"), rs.getLong("tokens"),
                rs.getDouble("embedding_cost")));

        List<RecentQuery> recent = queryLogs.findTop20ByOrderByCreatedAtDesc().stream()
                .map(q -> new RecentQuery(q.getId(), q.getUsername(), q.getQuestion(), q.isGrounded(),
                        q.isCacheHit(), q.getSourcesCount(), q.getLatencyMs(), q.getCostUsd(), q.getCreatedAt()))
                .toList();

        return new Dashboard(days, summary, daily, corpus, recent);
    }
}
