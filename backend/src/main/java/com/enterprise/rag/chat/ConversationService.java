package com.enterprise.rag.chat;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.enterprise.rag.common.ApiException;
import com.enterprise.rag.security.AuthUser;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Saves every answered question into the user's chat history, so chats survive sign-out,
 * new tabs and restarts.
 */
@Service
public class ConversationService {

    static final int TITLE_LENGTH = 80;

    private final ChatService chatService;
    private final ConversationRepository repository;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate tx;

    public ConversationService(ChatService chatService, ConversationRepository repository, ObjectMapper objectMapper,
            TransactionTemplate tx) {
        this.chatService = chatService;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.tx = tx;
    }

    /** The answer plus the conversation it was saved to; unwrapped so the JSON stays an AskResponse. */
    public record AskReply(long conversationId, @JsonUnwrapped AskResponse response) {
    }

    public record Turn(long id, String question, AskResponse response, Instant createdAt) {
    }

    public record Detail(long id, String title, Instant createdAt, Instant updatedAt, List<Turn> messages) {
    }

    /**
     * Answers the question and appends it to {@code conversationId}, or to a new conversation when null.
     * Ownership is checked before spending tokens; nothing is saved if answering fails.
     */
    public AskReply ask(String question, Long conversationId, AuthUser user) {
        if (conversationId != null) {
            requireOwned(conversationId, user);
        }
        AskResponse response = chatService.ask(question, user);
        // The LLM call stays outside the transaction; only the inserts share one.
        Long id = tx.execute(status -> save(conversationId, question, response, user));
        return new AskReply(id, response);
    }

    public List<ConversationRepository.Summary> list(AuthUser user) {
        return repository.list(user.username());
    }

    public Detail get(long id, AuthUser user) {
        ConversationRepository.Summary c = requireOwned(id, user);
        List<Turn> turns = repository.messages(id).stream()
                .map(m -> new Turn(m.id(), m.question(), fromJson(m.responseJson()), m.createdAt()))
                .toList();
        return new Detail(c.id(), c.title(), c.createdAt(), c.updatedAt(), turns);
    }

    public void delete(long id, AuthUser user) {
        if (!repository.delete(id, user.username())) {
            throw ApiException.notFound("Conversation not found");
        }
    }

    private long save(Long conversationId, String question, AskResponse response, AuthUser user) {
        long id = conversationId != null ? conversationId : repository.create(user.username(), titleFor(question));
        repository.addMessage(id, question, toJson(response));
        return id;
    }

    static String titleFor(String question) {
        String oneLine = question.strip().replaceAll("\\s+", " ");
        return oneLine.length() <= TITLE_LENGTH ? oneLine : oneLine.substring(0, TITLE_LENGTH - 1).strip() + "…";
    }

    private ConversationRepository.Summary requireOwned(long id, AuthUser user) {
        return repository.find(id, user.username()).orElseThrow(() -> ApiException.notFound("Conversation not found"));
    }

    private String toJson(AskResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialise answer", e);
        }
    }

    private AskResponse fromJson(String json) {
        try {
            return objectMapper.readValue(json, AskResponse.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not read saved answer", e);
        }
    }
}
