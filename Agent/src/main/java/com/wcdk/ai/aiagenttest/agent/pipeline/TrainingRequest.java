package com.wcdk.ai.aiagenttest.agent.pipeline;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record TrainingRequest(
        @NotEmpty(message = "samples must not be empty.")
        List<@Valid TrainingSample> samples,
        Integer epochs,
        String outputDirectory
) {
}
