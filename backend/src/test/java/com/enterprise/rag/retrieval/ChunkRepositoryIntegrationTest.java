package com.enterprise.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.enterprise.rag.ingestion.ChunkDraft;
import com.enterprise.rag.security.AuthUser;
import com.enterprise.rag.security.Role;

/**
 * Runs the real schema and SQL against pgvector to prove that RBAC filtering happens in the database.
 * Skipped automatically when Docker is not available.
 */
@Testcontainers(disabledWithoutDocker = true)
class ChunkRepositoryIntegrationTest {

    private static final int DIM = 1536;

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse("pgvector/pgvector:pg16").asCompatibleSubstituteFor("postgres"));

    static JdbcTemplate jdbc;
    static ChunkRepository repository;

    @BeforeAll
    static void setUp() {
        DriverManagerDataSource ds = new DriverManagerDataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword());
        Flyway.configure().dataSource(ds).load().migrate();
        jdbc = new JdbcTemplate(ds);
        repository = new ChunkRepository(jdbc);

        long handbook = insertDocument("Employee Handbook", "{EMPLOYEE}", "h1");
        long salaries = insertDocument("Compensation Guidelines", "{HR}", "h2");
        repository.replaceChunks(handbook,
                List.of(new ChunkDraft(0, "Employees receive 20 days of paid annual leave.", 10, 2, null)),
                List.of(unitVector(0)));
        repository.replaceChunks(salaries,
                List.of(new ChunkDraft(0, "The L5 salary band is 160,000 to 205,000 dollars.", 12, null, "Salary Bands")),
                List.of(unitVector(1)));
    }

    @Test
    void vectorSearchOnlyReturnsPermittedDocuments() {
        AuthUser employee = new AuthUser(1L, "dave", Set.of(Role.EMPLOYEE));
        // Query vector points straight at the HR-only chunk...
        List<ScoredChunk> results = repository.vectorSearch(unitVector(1), employee, 10);

        // ...but the employee can only ever get the handbook back.
        assertThat(results).extracting(ScoredChunk::documentTitle).containsExactly("Employee Handbook");
    }

    @Test
    void hrSeesSalaryChunkAsTopVectorHit() {
        AuthUser hr = new AuthUser(2L, "alice", Set.of(Role.HR, Role.EMPLOYEE));
        List<ScoredChunk> results = repository.vectorSearch(unitVector(1), hr, 10);

        assertThat(results.get(0).documentTitle()).isEqualTo("Compensation Guidelines");
        assertThat(results.get(0).score()).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(results.get(0).section()).isEqualTo("Salary Bands");
    }

    @Test
    void keywordSearchRespectsPermissionsAndAdminBypass() {
        String query = KeywordQueryBuilder.toOrQuery("salary band L5").orElseThrow();

        assertThat(repository.keywordSearch(query, new AuthUser(1L, "dave", Set.of(Role.EMPLOYEE)), 10)).isEmpty();
        assertThat(repository.keywordSearch(query, new AuthUser(3L, "admin", Set.of(Role.ADMIN)), 10))
                .extracting(ScoredChunk::documentTitle).containsExactly("Compensation Guidelines");
    }

    @Test
    void keywordSearchUsesStemming() {
        String query = KeywordQueryBuilder.toOrQuery("how many leaves do employees receive").orElseThrow();
        List<ScoredChunk> results = repository.keywordSearch(query, new AuthUser(1L, "dave", Set.of(Role.EMPLOYEE)), 10);

        assertThat(results).singleElement().satisfies(c -> assertThat(c.pageNumber()).isEqualTo(2));
    }

    private static long insertDocument(String title, String roles, String hash) {
        return jdbc.queryForObject("""
                INSERT INTO documents (title, filename, source_type, content_hash, size_bytes, allowed_roles, status)
                VALUES (?, ?, 'MARKDOWN', ?, 1, ?::text[], 'READY') RETURNING id""",
                Long.class, title, title + ".md", hash, roles);
    }

    private static float[] unitVector(int hotIndex) {
        float[] v = new float[DIM];
        v[hotIndex] = 1f;
        return v;
    }
}
