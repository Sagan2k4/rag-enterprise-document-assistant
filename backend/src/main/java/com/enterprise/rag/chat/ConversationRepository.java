package com.enterprise.rag.chat;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/**
 * Chat history storage. Every query is scoped by username, so one user can never read or
 * change another user's conversations.
 */
@Repository
public class ConversationRepository {

    public record Summary(long id, String title, Instant createdAt, Instant updatedAt) {
    }

    public record StoredMessage(long id, String question, String responseJson, Instant createdAt) {
    }

    private final JdbcTemplate jdbc;

    public ConversationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Summary> list(String username) {
        return jdbc.query("""
                SELECT id, title, created_at, updated_at FROM conversations
                WHERE username = ? ORDER BY updated_at DESC, id DESC LIMIT 200
                """, (rs, i) -> new Summary(rs.getLong("id"), rs.getString("title"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), username);
    }

    public Optional<Summary> find(long id, String username) {
        return jdbc.query("""
                SELECT id, title, created_at, updated_at FROM conversations WHERE id = ? AND username = ?
                """, (rs, i) -> new Summary(rs.getLong("id"), rs.getString("title"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant()), id, username)
                .stream().findFirst();
    }

    public long create(String username, String title) {
        GeneratedKeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO conversations (username, title) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, title);
            return ps;
        }, keys);
        return ((Number) keys.getKeys().get("id")).longValue();
    }

    public void addMessage(long conversationId, String question, String responseJson) {
        jdbc.update("INSERT INTO chat_messages (conversation_id, question, response_json) VALUES (?, ?, ?)",
                conversationId, question, responseJson);
        jdbc.update("UPDATE conversations SET updated_at = now() WHERE id = ?", conversationId);
    }

    public List<StoredMessage> messages(long conversationId) {
        return jdbc.query("""
                SELECT id, question, response_json, created_at FROM chat_messages
                WHERE conversation_id = ? ORDER BY id
                """, (rs, i) -> new StoredMessage(rs.getLong("id"), rs.getString("question"),
                rs.getString("response_json"), rs.getTimestamp("created_at").toInstant()), conversationId);
    }

    public boolean delete(long id, String username) {
        return jdbc.update("DELETE FROM conversations WHERE id = ? AND username = ?", id, username) > 0;
    }
}
