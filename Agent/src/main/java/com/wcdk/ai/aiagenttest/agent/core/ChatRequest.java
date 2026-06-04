package com.wcdk.ai.aiagenttest.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "聊天请求")
public record ChatRequest(
        @Schema(description = "会话 ID，留空时自动创建新会话", example = "204cee67-2d85-4df3-9b1d-b9e56647875f")
        String sessionId,
        @NotBlank(message = "消息内容不能为空。")
        @Schema(description = "用户消息。系统会根据规则自动判断走聊天模型还是图片模型", example = "请画一只坐在月球上的橘猫", requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {
}
