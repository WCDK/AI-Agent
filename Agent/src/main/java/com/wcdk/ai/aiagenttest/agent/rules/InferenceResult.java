package com.wcdk.ai.aiagenttest.agent.rules;

public record InferenceResult(
        String intent,
        double confidence,
        double urgency
) {
}
