package com.wcdk.ai.agent.core;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "聊天流式事件")
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record ChatStreamEvent(
        @Schema(description = "事件类型：meta、thinking、delta、done、error")
        String type,
        @Schema(description = "会话 ID")
        String sessionId,
        @Schema(description = "当前模型", example = "qwen2.5-coder:7b")
        String model,
        @Schema(description = "模型路由", example = "chat")
        String modelRoute,
        @Schema(description = "事件内容")
        String content,
        @Schema(description = "生成的图片")
        List<GeneratedImage> images
) {
    public ChatStreamEvent(String type, String sessionId, String model, String modelRoute, String content) {
        this(type, sessionId, model, modelRoute, content, List.of());
    }
}
