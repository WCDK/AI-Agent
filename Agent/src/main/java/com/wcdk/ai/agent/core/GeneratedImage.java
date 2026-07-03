package com.wcdk.ai.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "生成图片响应数据")
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record GeneratedImage(
        @Schema(description = "Base64 编码的 PNG 图片")
        String b64Json,
        @Schema(description = "用于生成图片的提示词")
        String revisedPrompt
) {
}
