package com.wcdk.ai.aiagenttest.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record TtsRequest(
        @NotBlank(message = "text must not be blank.")
        @Schema(description = "Text to synthesize", example = "你好，我是 AI 助手。")
        String text,

        @Schema(description = "Microsoft Edge TTS voice", example = "zh-CN-XiaoxiaoNeural")
        String voice
) {
}
