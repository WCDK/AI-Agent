package com.wcdk.ai.aiagenttest.agent.core;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Chat stream event")
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record ChatStreamEvent(
        @Schema(description = "Event type: meta, thinking, delta, done, error")
        String type,
        @Schema(description = "Session ID")
        String sessionId,
        @Schema(description = "Current model", example = "deepseek-r1:7b")
        String model,
        @Schema(description = "Model route", example = "chat")
        String modelRoute,
        @Schema(description = "Event content")
        String content,
        @Schema(description = "Generated images")
        List<GeneratedImage> images
) {
    public ChatStreamEvent(String type, String sessionId, String model, String modelRoute, String content) {
        this(type, sessionId, model, modelRoute, content, List.of());
    }
}
