package com.wcdk.ai.agent.pipeline;

import com.wcdk.ai.agent.rules.InferenceResult;
import com.wcdk.ai.agent.rules.RuleDecisionModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleDecisionModuleTests {

    @Test
    void drawingIntentFallsBackToChat() {
        var module = new RuleDecisionModule();

        var perception = new PerceptionResult("draw a cat in space", 1, false, false, false, false, true);
        var inference = new InferenceResult("DRAW_IMAGE", 0.9, 0.2);

        var decision = module.decide(perception, inference);

        assertThat(decision.action()).isEqualTo("CHAT");
        assertThat(decision.modelRoute()).isEqualTo("chat");
    }

    @Test
    void routesExecuteTaskIntentToChatModel() {
        var module = new RuleDecisionModule();

        var perception = new PerceptionResult("run the local checks", 1, false, false, false, false, true);
        var inference = new InferenceResult("EXECUTE_TASK", 0.9, 0.2);

        var decision = module.decide(perception, inference);

        assertThat(decision.action()).isEqualTo("EXECUTE_TASK");
        assertThat(decision.modelRoute()).isEqualTo("chat");
    }

    @Test
    void routesAnswerQuestionIntentToChatModel() {
        var module = new RuleDecisionModule();

        var perception = new PerceptionResult("what is Java", 1, false, false, false, false, true);
        var inference = new InferenceResult("ANSWER_QUESTION", 0.9, 0.2);

        var decision = module.decide(perception, inference);

        assertThat(decision.action()).isEqualTo("ANSWER_QUESTION");
        assertThat(decision.modelRoute()).isEqualTo("chat");
    }
}
