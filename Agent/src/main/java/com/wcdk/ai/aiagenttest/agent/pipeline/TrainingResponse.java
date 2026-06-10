package com.wcdk.ai.aiagenttest.agent.pipeline;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
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
