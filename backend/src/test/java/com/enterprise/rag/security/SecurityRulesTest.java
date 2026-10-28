package com.enterprise.rag.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.enterprise.rag.chat.AskResponse;
import com.enterprise.rag.chat.ChatController;
import com.enterprise.rag.chat.ConversationService;
import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.config.SecurityConfig;
import com.enterprise.rag.document.DocumentController;
import com.enterprise.rag.document.DocumentService;

/** Verifies the HTTP-level authorization rules with real JWTs. */
@WebMvcTest(controllers = { ChatController.class, DocumentController.class })
@Import({ SecurityConfig.class, JwtService.class })
@EnableConfigurationProperties(RagProperties.class)
class SecurityRulesTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtService jwtService;

    @MockitoBean
    ConversationService conversationService;

    @MockitoBean
    DocumentService documentService;

    @Test
    void requestsWithoutTokenAreRejected() throws Exception {
        mvc.perform(get("/api/documents")).andExpect(status().isUnauthorized());
    }

    @Test
    void forgedRoleClaimIsRejected() throws Exception {
        // Keep the employee's signature but swap in a payload claiming ADMIN.
        String[] employee = token(Role.EMPLOYEE).split("\\.");
        String[] admin = token(Role.ADMIN).split("\\.");
        String forged = employee[0] + "." + admin[1] + "." + employee[2];

        mvc.perform(get("/api/documents").header("Authorization", "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeCanAskQuestions() throws Exception {
        when(conversationService.ask(any(), any(), any())).thenReturn(new ConversationService.AskReply(7,
                new AskResponse("20 days [1].", List.of(), true, false, "gemini-3.5-flash",
                        new AskResponse.Metrics(10, 5, 5, 100, 10, 8, 0.0001))));

        mvc.perform(post("/api/chat/ask").header("Authorization", "Bearer " + token(Role.EMPLOYEE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"How much leave?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("20 days [1]."))
                .andExpect(jsonPath("$.conversationId").value(7));
    }

    @Test
    void chatHistoryRequiresToken() throws Exception {
        mvc.perform(get("/api/chat/conversations")).andExpect(status().isUnauthorized());
    }

    @Test
    void blankQuestionIsBadRequest() throws Exception {
        mvc.perform(post("/api/chat/ask").header("Authorization", "Bearer " + token(Role.EMPLOYEE))
                .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyAdminCanDeleteDocuments() throws Exception {
        mvc.perform(delete("/api/documents/1").header("Authorization", "Bearer " + token(Role.HR)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/documents/1").header("Authorization", "Bearer " + token(Role.ADMIN)))
                .andExpect(status().isNoContent());
    }

    private String token(Role role) {
        return jwtService.generateToken(new AuthUser(1L, "user-" + role, Set.of(role)));
    }
}
