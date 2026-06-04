package com.wcdk.ai.aiagenttest.agent.pipeline;

import java.util.List;

import com.wcdk.ai.aiagenttest.agent.core.OllamaMessage;
import com.wcdk.ai.aiagenttest.agent.rules.DecisionResult;
import com.wcdk.ai.aiagenttest.agent.rules.InferenceResult;

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
