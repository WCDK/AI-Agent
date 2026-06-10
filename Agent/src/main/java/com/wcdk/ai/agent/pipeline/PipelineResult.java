package com.wcdk.ai.agent.pipeline;

import java.util.List;

import com.wcdk.ai.agent.core.OllamaMessage;
import com.wcdk.ai.agent.rules.DecisionResult;
import com.wcdk.ai.agent.rules.InferenceResult;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record PipelineResult(
        List<OllamaMessage> messages,
        PerceptionResult perception,
        InferenceResult inference,
        DecisionResult decision
) {
    public String traceSummary() {
        return "perception=" + perception
                + ", inference=" + inference
                + ", decision=" + decision;
    }
}
