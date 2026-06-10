package com.wcdk.ai.agent.pipeline;

import com.wcdk.ai.agent.rules.InferenceResult;
import com.wcdk.ai.config.WcdkProperties;
import com.wcdk.ai.agent.rules.RuleDecisionModule;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

class RuleDecisionModuleTests {

    @Test
    void routesDrawingRequestsToImageModel() {
        var module = new RuleDecisionModule(new WcdkProperties(), new DefaultResourceLoader());

        var perception = new PerceptionResult("请帮我生成图片，一只太空猫", 1, false, false, true, false, true);
        var inference = new InferenceResult("CHAT", 0.9, 0.2);

        var decision = module.decide(perception, inference);

        assertThat(decision.action()).isEqualTo("GENERATE_IMAGE");
        assertThat(decision.modelRoute()).isEqualTo("image");
    }
}
