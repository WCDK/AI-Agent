package com.wcdk.ai.agent.core;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Stable Diffusion WebUI txt2img request")
public record Txt2ImgRequest(
        @NotBlank(message = "正向提示词不能为空。")
        @Schema(description = "Positive prompt", requiredMode = Schema.RequiredMode.REQUIRED)
        String positivePrompt,
        @Schema(description = "Negative prompt")
        String negativePrompt,
        @Schema(description = "LoRA prompt fragment, for example <lora:add_detail:0.7>")
        String loraPrompt
) {
}
