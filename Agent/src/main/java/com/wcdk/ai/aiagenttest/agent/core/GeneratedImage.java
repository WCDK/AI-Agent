package com.wcdk.ai.aiagenttest.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Generated image payload")
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record GeneratedImage(
        @Schema(description = "PNG image encoded as base64")
        String b64Json,
        @Schema(description = "Prompt used to generate this image")
        String revisedPrompt
) {
}
