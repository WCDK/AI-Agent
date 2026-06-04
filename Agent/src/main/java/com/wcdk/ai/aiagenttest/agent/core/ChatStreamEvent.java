package com.wcdk.ai.aiagenttest.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "聊天流式事件")
public record ChatStreamEvent(
        @Schema(description = "事件类型：meta、thinking、delta、done、error")
        String type,
        @Schema(description = "会话 ID")
        String sessionId,
        @Schema(description = "当前实际使用的模型", example = "deepseek-r1:7b")
        String model,
        @Schema(description = "模型路由", example = "chat")
        String modelRoute,
        @Schema(description = "事件内容。delta 事件通常承载回答片段")
        String content
) {
}
