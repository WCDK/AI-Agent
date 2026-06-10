package com.wcdk.ai.agent.pipeline;

import jakarta.validation.constraints.NotBlank;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record TrainingSample(
        @NotBlank(message = "message must not be blank.")
        String message,
        @NotBlank(message = "intent must not be blank.")
        String intent
) {
}
