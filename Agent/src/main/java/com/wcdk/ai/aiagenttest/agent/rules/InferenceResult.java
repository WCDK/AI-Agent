package com.wcdk.ai.aiagenttest.agent.rules;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record InferenceResult(
        String intent,
        double confidence,
        double urgency
) {
}
