package com.enterprise.rag.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import com.enterprise.rag.common.ApiException;
import com.enterprise.rag.security.AuthUser;
import com.enterprise.rag.security.Role;
import com.fasterxml.jackson.databind.ObjectMapper;

class ConversationServiceTest {

    private final ChatService chatService = mock(ChatService.class);
    private final ConversationRepository repository = mock(ConversationRepository.class);
    private final TransactionTemplate tx = mock(TransactionTemplate.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ConversationService service = new ConversationService(chatService, repository, objectMapper, tx);
    private final AuthUser bob = new AuthUser(2L, "bob", Set.of(Role.ENGINEERING));

    private final AskResponse answer = new AskResponse("15 minutes [1].", List.of(), true, false, "gemini",
            new AskResponse.Metrics(10, 5, 5, 100, 10, 8, 0.0001));

    @Test
    @SuppressWarnings("unchecked")
    void firstQuestionStartsANewConversation() {
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Long>) inv.getArgument(0)).doInTransaction(null));
        when(chatService.ask("SEV1 ack time?", bob)).thenReturn(answer);
        when(repository.create("bob", "SEV1 ack time?")).thenReturn(42L);

        ConversationService.AskReply reply = service.ask("SEV1 ack time?", null, bob);

        assertThat(reply.conversationId()).isEqualTo(42L);
        verify(repository).addMessage(eq(42L), eq("SEV1 ack time?"), anyString());
    }

    @Test
    void cannotContinueSomeoneElsesConversation() {
        when(repository.find(9L, "bob")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.ask("hi", 9L, bob)).isInstanceOf(ApiException.class);
        verify(chatService, never()).ask(any(), any());
    }

    @Test
    void savedAnswerRoundTripsThroughJson() throws Exception {
        var summary = new ConversationRepository.Summary(5, "t", java.time.Instant.now(), java.time.Instant.now());
        when(repository.find(5L, "bob")).thenReturn(Optional.of(summary));
        when(repository.messages(5L)).thenReturn(List.of(new ConversationRepository.StoredMessage(1, "q",
                objectMapper.writeValueAsString(answer), java.time.Instant.now())));

        assertThat(service.get(5L, bob).messages().get(0).response()).isEqualTo(answer);
    }

    @Test
    void titleIsFirstQuestionOnOneLineAndTrimmed() {
        assertThat(ConversationService.titleFor("  What is\n the  deploy window? ")).isEqualTo("What is the deploy window?");
        assertThat(ConversationService.titleFor("x".repeat(200))).hasSize(ConversationService.TITLE_LENGTH);
    }
}
