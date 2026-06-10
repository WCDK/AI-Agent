package com.wcdk.ai.agent.rules;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record DecisionResult(
        String action,
        String modelRoute,
        String systemInstruction,
        boolean executable
) {
}
