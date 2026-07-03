package com.wcdk.ai.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "聊天请求")
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record ChatRequest(
        @Schema(description = "会话 ID，留空时自动创建新会话。", example = "204cee67-2d85-4df3-9b1d-b9e56647875f")
        String sessionId,
        @NotBlank(message = "消息内容不能为空。")
        @Schema(description = "用户消息。", example = "用三句话解释什么是 AI Agent。", requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
