package com.wcdk.ai.aiagenttest.agent.pipeline;

public record TrainingResponse(
        String status,
        int newSampleCount,
        int totalSampleCount,
        int epochs,
        String modelPath,
        String versionedModelPath,
        String labelsPath,
        String metadataPath,
        String samplesPath,
        String modelSource
) {
}
