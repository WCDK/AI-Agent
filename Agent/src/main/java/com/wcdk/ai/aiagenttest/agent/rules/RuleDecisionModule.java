package com.wcdk.ai.aiagenttest.agent.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.wcdk.ai.aiagenttest.agent.pipeline.PerceptionResult;
import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RuleDecisionModule {

    private final WcdkProperties properties;
    private final ResourceLoader resourceLoader;

    public RuleDecisionModule(WcdkProperties properties, ResourceLoader resourceLoader) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
    }

    public DecisionResult decide(PerceptionResult perception, InferenceResult inference) {
        var decision = new AtomicReference<DecisionResult>();
        KieSession kieSession = newKieSession();

        try {
            kieSession.setGlobal("decision", decision);
            kieSession.insert(perception);
            kieSession.insert(inference);
            kieSession.fireAllRules();
        } finally {
            kieSession.dispose();
        }

        if (decision.get() != null) {
            return decision.get();
        }

        return new DecisionResult(
                "CHAT",
                "chat",
                "用户正在进行普通对话。请保持回答简洁、准确，并主动补齐必要上下文。",
                true
        );
    }

    private KieSession newKieSession() {
        var decisionDrl = readDecisionDrl();
        if (!StringUtils.hasText(decisionDrl)) {
            throw new IllegalStateException("Drools 决策规则内容不能为空。");
        }

        var kieHelper = new KieHelper();
        kieHelper.addContent(decisionDrl, ResourceType.DRL);
        return kieHelper.build().newKieSession();
    }

    private String readDecisionDrl() {
        var rulePath = properties.getRules().getDrools().getDecisionRulePath();
        if (!StringUtils.hasText(rulePath)) {
            throw new IllegalStateException("wcdk.rules.drools.decision-rule-path 不能为空。");
        }

        try {
            var resource = resourceLoader.getResource(rulePath);
            if (!resource.exists()) {
                throw new IllegalStateException("Drools 规则文件不存在: " + rulePath);
            }
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Drools 规则文件加载失败: " + rulePath, exception);
        }
    }
}
