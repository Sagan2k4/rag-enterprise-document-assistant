package com.enterprise.rag.evaluation;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;

import com.enterprise.rag.chat.PromptBuilder;
import com.enterprise.rag.observability.CostCalculator;
import com.enterprise.rag.retrieval.RetrievedChunk;

/** LLM-as-a-judge: scores how well an answer is supported by the retrieved passages (1–5). */
@Component
public class FaithfulnessJudge {

    private static final Pattern SCORE = Pattern.compile("[1-5]");

    private static final String JUDGE_PROMPT = """
            You are a strict evaluator of retrieval-augmented answers.
            Score how faithful the ANSWER is to the CONTEXT on a 1-5 scale:
            5 = every claim is directly supported by the context
            4 = all key claims supported, minor harmless additions
            3 = mostly supported, at least one unsupported detail
            2 = several unsupported or distorted claims
            1 = mostly unsupported or contradicts the context
            Respond with the single digit only.""";

    private final ChatClient chatClient;
    private final CostCalculator costCalculator;

    public FaithfulnessJudge(ChatClient chatClient, CostCalculator costCalculator) {
        this.chatClient = chatClient;
        this.costCalculator = costCalculator;
    }

    public record Verdict(Integer score, double costUsd) {
    }

    public Verdict judge(String question, String answer, List<RetrievedChunk> context) {
        String user = PromptBuilder.userMessage(question, context) + "\n\nANSWER:\n" + answer;
        ChatResponse response = chatClient.prompt(new Prompt(List.of(
                new SystemMessage(JUDGE_PROMPT), new UserMessage(user)))).call().chatResponse();
        if (response == null || response.getResult() == null) {
            return new Verdict(null, 0);
        }
        Usage usage = response.getMetadata().getUsage();
        double cost = usage == null ? 0 : costCalculator.chatCost(
                usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens());
        Matcher m = SCORE.matcher(response.getResult().getOutput().getText());
        return new Verdict(m.find() ? Integer.parseInt(m.group()) : null, cost);
    }
}
