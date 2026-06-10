package com.wcdk.ai.aiagenttest.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Agent 健康检查响应")
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record AgentHealthResponse(
        @Schema(description = "服务状态", example = "UP")
        String status,
        @Schema(description = "当前使用的 Ollama 模型", example = "deepseek-r1:7b")
        String model,
        @Schema(description = "Ollama 服务地址", example = "http://localhost:11434")
        String ollamaBaseUrl
) {
}
