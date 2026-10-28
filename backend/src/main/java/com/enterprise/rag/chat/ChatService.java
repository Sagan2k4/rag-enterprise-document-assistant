package com.enterprise.rag.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.enterprise.rag.observability.CostCalculator;
import com.enterprise.rag.observability.QueryLog;
import com.enterprise.rag.observability.QueryLogRepository;
import com.enterprise.rag.retrieval.HybridSearchService;
import com.enterprise.rag.retrieval.RetrievedChunk;
import com.enterprise.rag.security.AuthUser;

/**
 * Answers a question: cache → permission-aware hybrid retrieval → grounded generation → citation check.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final HybridSearchService search;
    private final ChatClient chatClient;
    private final AnswerCache cache;
    private final CostCalculator costCalculator;
    private final QueryLogRepository queryLogs;

    public ChatService(HybridSearchService search, ChatClient chatClient, AnswerCache cache,
            CostCalculator costCalculator, QueryLogRepository queryLogs) {
        this.search = search;
        this.chatClient = chatClient;
        this.cache = cache;
        this.costCalculator = costCalculator;
        this.queryLogs = queryLogs;
    }

    /** Result of a full RAG pass, including the retrieved chunks (used by the evaluation suite). */
    public record Generation(AskResponse response, List<RetrievedChunk> retrieved) {
    }

    public AskResponse ask(String question, AuthUser user) {
        long start = System.nanoTime();
        Optional<AskResponse> cached = cache.get(question, user);
        AskResponse response;
        if (cached.isPresent()) {
            response = cached.get().asCacheHit(elapsedMs(start));
        } else {
            response = generate(question, user).response();
            if (response.grounded()) {
                cache.put(question, user, response);
            }
        }
        record(user, question, response);
        return response;
    }

    public Generation generate(String question, AuthUser user) {
        long start = System.nanoTime();
        HybridSearchService.Result retrieval = search.search(question, user);
        List<RetrievedChunk> chunks = retrieval.chunks();
        int embeddingTokens = retrieval.embeddingTokens();

        // Nothing the user is allowed to see matched: refuse without spending tokens on the LLM.
        if (chunks.isEmpty()) {
            AskResponse.Metrics metrics = new AskResponse.Metrics(elapsedMs(start), retrieval.latencyMs(), 0,
                    0, 0, embeddingTokens, costCalculator.embeddingCost(embeddingTokens));
            return new Generation(new AskResponse(PromptBuilder.NO_ANSWER, List.of(), false, false, null, metrics),
                    chunks);
        }

        long generationStart = System.nanoTime();
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PromptBuilder.SYSTEM_PROMPT),
                new UserMessage(PromptBuilder.userMessage(question, chunks))));
        ChatResponse chatResponse = chatClient.prompt(prompt).call().chatResponse();
        long generationMs = elapsedMs(generationStart);

        String answer = chatResponse == null || chatResponse.getResult() == null
                ? PromptBuilder.NO_ANSWER
                : chatResponse.getResult().getOutput().getText();
        Usage usage = chatResponse == null ? null : chatResponse.getMetadata().getUsage();
        int promptTokens = usage == null ? 0 : nullToZero(usage.getPromptTokens());
        int completionTokens = usage == null ? 0 : nullToZero(usage.getCompletionTokens());
        String model = chatResponse == null ? null : chatResponse.getMetadata().getModel();

        Set<Integer> cited = CitationExtractor.extract(answer, chunks.size());
        List<AskResponse.Source> sources = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            sources.add(AskResponse.Source.from(i + 1, chunks.get(i), cited.contains(i + 1)));
        }
        boolean grounded = !cited.isEmpty() && !PromptBuilder.isRefusal(answer);
        if (!grounded && !PromptBuilder.isRefusal(answer)) {
            log.warn("Answer without citations for question '{}'", question);
        }

        AskResponse.Metrics metrics = new AskResponse.Metrics(elapsedMs(start), retrieval.latencyMs(), generationMs,
                promptTokens, completionTokens, embeddingTokens,
                costCalculator.totalCost(promptTokens, completionTokens, embeddingTokens));
        return new Generation(new AskResponse(answer, sources, grounded, false, model, metrics), chunks);
    }

    private void record(AuthUser user, String question, AskResponse response) {
        try {
            queryLogs.save(QueryLog.of(user.username(), question, response));
        } catch (Exception e) {
            log.warn("Could not persist query log: {}", e.getMessage());
        }
    }

    private static int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
