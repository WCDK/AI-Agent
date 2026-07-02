package com.wcdk.ai.agent.pipeline;

import jakarta.validation.constraints.NotBlank;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record TrainingSample(
        @NotBlank(message = "训练消息不能为空。")
        String message,
        @NotBlank(message = "训练意图不能为空。")
        String intent
) {
}
