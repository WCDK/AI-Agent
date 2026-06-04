package com.wcdk.ai.aiagenttest.agent.pipeline;

public record PerceptionResult(
        String normalizedMessage,
        int tokenCount,
        boolean question,
        boolean command,
        boolean risky,
        boolean chinese
) {
}
