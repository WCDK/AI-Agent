package com.wcdk.ai.aiagenttest.agent.rules;

public record DecisionResult(
        String action,
        String modelRoute,
        String systemInstruction,
        boolean executable
) {
}
