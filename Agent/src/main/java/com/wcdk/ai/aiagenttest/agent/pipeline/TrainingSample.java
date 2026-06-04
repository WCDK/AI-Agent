package com.wcdk.ai.aiagenttest.agent.pipeline;

import jakarta.validation.constraints.NotBlank;

public record TrainingSample(
        @NotBlank(message = "message must not be blank.")
        String message,
        @NotBlank(message = "intent must not be blank.")
        String intent
) {
}
