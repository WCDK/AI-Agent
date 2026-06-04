package com.wcdk.ai.aiagenttest.agent.pipeline;

import java.util.ArrayList;
import java.util.List;

import com.wcdk.ai.aiagenttest.agent.core.OllamaMessage;
import com.wcdk.ai.aiagenttest.agent.rules.DecisionResult;
import com.wcdk.ai.aiagenttest.agent.rules.InferenceResult;
import org.springframework.stereotype.Component;

@Component
public class OllamaExecutionModule {

    public List<OllamaMessage> buildMessages(
            String baseSystemPrompt,
            DecisionResult decision,
            InferenceResult inference,
            List<OllamaMessage> history
    ) {
        var messages = new ArrayList<OllamaMessage>();
        messages.add(new OllamaMessage("system", baseSystemPrompt + "\n\n"
                + "Agent pipeline:\n"
                + "- 推理意图: " + inference.intent() + "\n"
                + "- 推理置信度: " + String.format("%.4f", inference.confidence()) + "\n"
                + "- 决策动作: " + decision.action() + "\n"
                + "- 决策规则: " + decision.systemInstruction()));
        messages.addAll(history);
        return messages;
    }
}
