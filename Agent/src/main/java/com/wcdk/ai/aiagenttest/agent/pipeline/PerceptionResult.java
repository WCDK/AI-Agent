package com.wcdk.ai.aiagenttest.agent.pipeline;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record PerceptionResult(
        String normalizedMessage,
        int tokenCount,
        boolean question,
        boolean command,
        boolean image,
        boolean risky,
        boolean chinese
) {
}
