package com.wcdk.ai.aiagenttest.agent.pipeline;

import java.util.List;

import com.wcdk.ai.aiagenttest.agent.core.OllamaMessage;
import com.wcdk.ai.aiagenttest.agent.rules.DecisionResult;
import com.wcdk.ai.aiagenttest.agent.rules.RuleDecisionModule;
import org.springframework.stereotype.Service;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Service
public class AgentPipeline {

    private final PerceptionModule perceptionModule;
    private final Dl4jInferenceModule inferenceModule;
    private final RuleDecisionModule decisionModule;
    private final OllamaExecutionModule executionModule;
    private final FeedbackModule feedbackModule;
    private final LearningModule learningModule;

    public AgentPipeline(
            PerceptionModule perceptionModule,
            Dl4jInferenceModule inferenceModule,
            RuleDecisionModule decisionModule,
            OllamaExecutionModule executionModule,
            FeedbackModule feedbackModule,
            LearningModule learningModule
    ) {
        this.perceptionModule = perceptionModule;
        this.inferenceModule = inferenceModule;
        this.decisionModule = decisionModule;
        this.executionModule = executionModule;
        this.feedbackModule = feedbackModule;
        this.learningModule = learningModule;
    }

    public PipelineResult prepare(
            String userMessage,
            String baseSystemPrompt,
            List<OllamaMessage> history
    ) {
        var perception = perceptionModule.perceive(userMessage);
        var inference = inferenceModule.infer(perception);
        var decision = decisionModule.decide(perception, inference);
        var messages = executionModule.buildMessages(baseSystemPrompt, decision, inference, history);
        return new PipelineResult(messages, perception, inference, decision);
    }

    public void learn(String sessionId, String userMessage, DecisionResult decision, String answer) {
        var feedback = feedbackModule.collect(sessionId, userMessage, decision, answer);
        learningModule.learn(feedback);
    }
}
