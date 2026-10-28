package com.enterprise.rag.chat;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.enterprise.rag.security.AuthUser;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ConversationService conversations;

    public ChatController(ConversationService conversations) {
        this.conversations = conversations;
    }

    /** {@code conversationId} continues an existing chat; omit it to start a new one. */
    public record AskRequest(@NotBlank @Size(max = 2000) String question, Long conversationId) {
    }

    @PostMapping("/ask")
    public ConversationService.AskReply ask(@Valid @RequestBody AskRequest request,
            @AuthenticationPrincipal AuthUser user) {
        return conversations.ask(request.question(), request.conversationId(), user);
    }

    @GetMapping("/conversations")
    public List<ConversationRepository.Summary> list(@AuthenticationPrincipal AuthUser user) {
        return conversations.list(user);
    }

    @GetMapping("/conversations/{id}")
    public ConversationService.Detail get(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        return conversations.get(id, user);
    }

    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @AuthenticationPrincipal AuthUser user) {
        conversations.delete(id, user);
    }
}
