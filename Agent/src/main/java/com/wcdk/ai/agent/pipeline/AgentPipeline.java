package com.wcdk.ai.agent.pipeline;

import java.util.List;

import com.wcdk.ai.agent.core.OllamaMessage;
import com.wcdk.ai.agent.rules.DecisionResult;
import com.wcdk.ai.agent.rules.RuleDecisionModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Service
public class AgentPipeline {

    private static final Logger log = LoggerFactory.getLogger(AgentPipeline.class);

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
        log.info(
                "用户意图: intent={}, confidence={}, urgency={}, question={}, command={}, image={}, risky={}",
                inference.intent(),
                String.format("%.4f", inference.confidence()),
                String.format("%.2f", inference.urgency()),
                perception.question(),
                perception.command(),
                perception.image(),
                perception.risky()
        );
        var decision = decisionModule.decide(perception, inference);
        var messages = executionModule.buildMessages(baseSystemPrompt, decision, inference, history);
        return new PipelineResult(messages, perception, inference, decision);
    }

    public void learn(String sessionId, String userMessage, DecisionResult decision, String answer) {
        var feedback = feedbackModule.collect(sessionId, userMessage, decision, answer);
        learningModule.learn(feedback);
    }
}
