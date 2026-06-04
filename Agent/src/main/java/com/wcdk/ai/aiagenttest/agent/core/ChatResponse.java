package com.wcdk.ai.aiagenttest.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "聊天响应")
public record ChatResponse(
        @Schema(description = "会话 ID，后续连续对话需要传回该值")
        String sessionId,
        @Schema(description = "当前实际使用的模型", example = "deepseek-r1:7b")
        String model,
        @Schema(description = "模型路由", example = "chat")
        String modelRoute,
        @Schema(description = "助手回复内容")
        String answer
) {
}
