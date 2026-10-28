package com.enterprise.rag.retrieval;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.enterprise.rag.ingestion.ChunkDraft;
import com.enterprise.rag.security.AuthUser;

/**
 * pgvector + Postgres full-text storage for chunks.
 * <p>
 * Permission filtering happens <em>inside</em> the SQL (array overlap on {@code documents.allowed_roles}),
 * so chunks a user cannot see never leave the database and can never reach the LLM prompt.
 */
@Repository
public class ChunkRepository {

    private static final String SELECT_COLUMNS = """
            c.id, c.document_id, d.title, d.filename, d.source_type, c.chunk_index,
            c.content, c.page_number, c.section""";

    private static final String ACCESS_FILTER = "d.status = 'READY' AND (? OR d.allowed_roles && ?::text[])";

    private static final String INSERT_SQL = """
            INSERT INTO document_chunks
                (document_id, chunk_index, content, page_number, section, token_count, embedding)
            VALUES (?, ?, ?, ?, ?, ?, ?::vector)""";

    private static final String VECTOR_SEARCH_SQL = """
            SELECT %s, 1 - (c.embedding <=> ?::vector) AS score
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            WHERE %s
            ORDER BY c.embedding <=> ?::vector
            LIMIT ?""".formatted(SELECT_COLUMNS, ACCESS_FILTER);

    private static final String KEYWORD_SEARCH_SQL = """
            SELECT %s, ts_rank_cd(c.content_tsv, q) AS score
            FROM document_chunks c
            JOIN documents d ON d.id = c.document_id
            CROSS JOIN to_tsquery('english', ?) AS q
            WHERE c.content_tsv @@ q AND %s
            ORDER BY score DESC
            LIMIT ?""".formatted(SELECT_COLUMNS, ACCESS_FILTER);

    private static final RowMapper<ScoredChunk> ROW_MAPPER = ChunkRepository::mapRow;

    private final JdbcTemplate jdbc;

    public ChunkRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Idempotent: re-ingesting a document replaces its chunks atomically. */
    @Transactional
    public void replaceChunks(long documentId, List<ChunkDraft> chunks, List<float[]> embeddings) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("chunks and embeddings must have the same size");
        }
        jdbc.update("DELETE FROM document_chunks WHERE document_id = ?", documentId);
        jdbc.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                ChunkDraft chunk = chunks.get(i);
                ps.setLong(1, documentId);
                ps.setInt(2, chunk.index());
                ps.setString(3, chunk.text());
                ps.setObject(4, chunk.pageNumber(), Types.INTEGER);
                ps.setString(5, chunk.section());
                ps.setInt(6, chunk.tokenCount());
                ps.setString(7, toVectorLiteral(embeddings.get(i)));
            }

            @Override
            public int getBatchSize() {
                return chunks.size();
            }
        });
    }

    /** Nearest neighbours by cosine distance (HNSW index), restricted to documents the user may read. */
    public List<ScoredChunk> vectorSearch(float[] queryEmbedding, AuthUser user, int limit) {
        String vector = toVectorLiteral(queryEmbedding);
        return jdbc.query(VECTOR_SEARCH_SQL, ROW_MAPPER,
                vector, user.isAdmin(), toTextArrayLiteral(user.roleNames()), vector, limit);
    }

    /**
     * Full-text search ranked by {@code ts_rank_cd}.
     *
     * @param tsQuery a sanitised {@code to_tsquery} expression, see {@link KeywordQueryBuilder}
     */
    public List<ScoredChunk> keywordSearch(String tsQuery, AuthUser user, int limit) {
        return jdbc.query(KEYWORD_SEARCH_SQL, ROW_MAPPER,
                tsQuery, user.isAdmin(), toTextArrayLiteral(user.roleNames()), limit);
    }

    static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 10).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    /** Role names are enum constants, so they never need quoting. */
    static String toTextArrayLiteral(String[] values) {
        return "{" + String.join(",", values) + "}";
    }

    private static ScoredChunk mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ScoredChunk(
                rs.getLong("id"),
                rs.getLong("document_id"),
                rs.getString("title"),
                rs.getString("filename"),
                rs.getString("source_type"),
                rs.getInt("chunk_index"),
                rs.getString("content"),
                rs.getObject("page_number", Integer.class),
                rs.getString("section"),
                rs.getDouble("score"));
    }
}
