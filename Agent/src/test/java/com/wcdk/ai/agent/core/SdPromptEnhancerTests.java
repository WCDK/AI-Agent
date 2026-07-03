package com.wcdk.ai.agent.core;

import com.wcdk.ai.agent.rules.DecisionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SdPromptEnhancerTests {

    private final SdPromptEnhancer enhancer = new SdPromptEnhancer();

    @Test
    void keepsUserPromptAsMainContentAndAddsOnlyMatchedEnhancements() {
        var decision = new DecisionResult(
                "GENERATE_IMAGE",
                "image",
                "",
                true,
                "",
                "",
                "",
                0.0
        );
        var userPrompt = "\u590f\u65e5\u6b63\u5348\u4e00\u76f4\u5728\u6811\u836b\u4e0b\u7761\u89c9\u7684 \u6175\u61d2\u7684\u6a58\u732b";

        var prompt = enhancer.enhance(userPrompt, decision);

        assertThat(prompt.positivePrompt())
                .contains(userPrompt)
                .contains("<lora:add_detail:0.7>")
                .contains("<lora:epi_noiseoffset2:0.6>")
                .contains("cat")
                .contains("orange tabby fur")
                .contains("under the shade of a tree")
                .contains("bright midday light")
                .contains("closed eyes");
        assertThat(prompt.positivePrompt()).doesNotContain("curled up on cool grass");
        assertThat(prompt.negativePrompt())
                .contains("worst quality")
                .contains("malformed paws")
                .contains("open eyes");
    }

    @Test
    void adaptsKeywordsForNonCatUserPrompt() {
        var decision = new DecisionResult(
                "GENERATE_IMAGE",
                "image",
                "",
                true,
                "",
                "",
                "",
                0.0
        );

        var prompt = enhancer.enhance("portrait of a girl standing in rainy night street", decision);

        assertThat(prompt.positivePrompt())
                .contains("portrait of a girl standing in rainy night street")
                .contains("natural portrait")
                .contains("rainy atmosphere")
                .contains("night lighting")
                .doesNotContain("orange tabby fur")
                .doesNotContain("under the shade of a tree");
        assertThat(prompt.negativePrompt())
                .contains("bad hands")
                .doesNotContain("malformed paws");
    }

    @Test
    void keepsRuleLoraWeightAndAddsMissingNoiseoffsetLora() {
        var decision = new DecisionResult(
                "GENERATE_IMAGE",
                "image",
                "",
                true,
                "",
                "EasyNegative, extra fingers",
                "<lora:add_detail:0.3>",
                0.3
        );

        var prompt = enhancer.enhance("draw a lazy orange cat sleeping under a tree", decision);

        assertThat(prompt.positivePrompt())
                .contains("<lora:add_detail:0.3>")
                .doesNotContain("<lora:add_detail:0.7>")
                .contains("<lora:epi_noiseoffset2:0.6>");
        assertThat(prompt.negativePrompt())
                .startsWith("EasyNegative, extra fingers")
                .contains("worst quality");
    }
}
