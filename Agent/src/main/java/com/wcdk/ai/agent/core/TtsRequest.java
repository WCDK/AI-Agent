package com.wcdk.ai.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
/**
 * @auther WCDK
 * @date 2026/7/2
 * @version 1.0
 **/
public record TtsRequest(
        @NotBlank(message = "语音合成文本不能为空。")
        @Schema(description = "需要合成语音的文本", example = "你好，我是 AI 助手。")
        String text,

        @Schema(description = "Microsoft Edge TTS 声音", example = "zh-CN-XiaoxiaoNeural")
        String voice
) {
}
