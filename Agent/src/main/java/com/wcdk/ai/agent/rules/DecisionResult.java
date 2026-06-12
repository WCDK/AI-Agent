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
        boolean executable,
        String imagePrompt,
        String negativePrompt,
        String loraSetting,
        double loraWeight
) {
    public DecisionResult(String action, String modelRoute, String systemInstruction, boolean executable) {
        this(action, modelRoute, systemInstruction, executable, "", "", "", 0.0D);
    }
}
