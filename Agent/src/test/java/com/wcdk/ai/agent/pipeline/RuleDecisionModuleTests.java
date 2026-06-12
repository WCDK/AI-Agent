package com.wcdk.ai.agent.pipeline;

import com.wcdk.ai.agent.rules.InferenceResult;
import com.wcdk.ai.agent.rules.RuleDecisionModule;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleDecisionModuleTests {

    @Test
    void routesDrawingIntentToImageModel() {
        var module = new RuleDecisionModule();

        var perception = new PerceptionResult("请帮我画一只太空猫", 1, false, false, false, false, true);
        var inference = new InferenceResult("DRAW_IMAGE", 0.9, 0.2);

        var decision = module.decide(perception, inference);

        assertThat(decision.action()).isEqualTo("GENERATE_IMAGE");
        assertThat(decision.modelRoute()).isEqualTo("image");
    }

    @Test
    void appliesPortraitPhotoDrawRule() {
        var module = new RuleDecisionModule();

        var perception = new PerceptionResult("请画一张真人写真", 1, false, false, true, false, true);
        var inference = new InferenceResult("DRAW_IMAGE", 0.9, 0.2);

        var decision = module.decide(perception, inference);

        assertThat(decision.action()).isEqualTo("GENERATE_IMAGE");
        assertThat(decision.imagePrompt()).isBlank();
        assertThat(decision.negativePrompt()).contains("EasyNegative");
        assertThat(decision.loraSetting()).isEqualTo("<lora:add_detail:0.3>");
        assertThat(decision.loraWeight()).isEqualTo(0.3);
    }

}
