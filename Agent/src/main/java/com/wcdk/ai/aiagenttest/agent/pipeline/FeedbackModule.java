package com.wcdk.ai.aiagenttest.agent.pipeline;

import java.time.Instant;

import com.wcdk.ai.aiagenttest.agent.rules.DecisionResult;
import org.springframework.stereotype.Component;

@Component
public class FeedbackModule {

    public FeedbackRecord collect(
            String sessionId,
            String userMessage,
            DecisionResult decision,
            String answer
    ) {
        return new FeedbackRecord(
                sessionId,
                userMessage,
                decision.action(),
                answer == null ? 0 : answer.length(),
                Instant.now()
        );
    }

    public record FeedbackRecord(
            String sessionId,
            String userMessage,
            String action,
            int answerLength,
            Instant createdAt
    ) {
    }
}
