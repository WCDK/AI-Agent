package com.wcdk.ai.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * @auther WCDK
 * @date 2026/7/2
 * @version 1.0
 **/
@Schema(description = "Stable Diffusion WebUI 文生图请求")
public record Txt2ImgRequest(
        @NotBlank(message = "正向提示词不能为空。")
        @Schema(description = "正向提示词", requiredMode = Schema.RequiredMode.REQUIRED)
        String positivePrompt,
        @Schema(description = "反向提示词")
        String negativePrompt,
        @Schema(description = "LoRA 提示词片段，例如 <lora:add_detail:0.7>")
        String loraPrompt
) {
}
