package com.wcdk.ai.agent.rules;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.wcdk.ai.agent.pipeline.PerceptionResult;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Component
public class RuleDecisionModule {

    private static final String RULE_LOCATION_PATTERN = "classpath*:rules/**/*.drl";

    private final PathMatchingResourcePatternResolver resourcePatternResolver;

    public RuleDecisionModule() {
        this(new PathMatchingResourcePatternResolver());
    }

    RuleDecisionModule(PathMatchingResourcePatternResolver resourcePatternResolver) {
        this.resourcePatternResolver = resourcePatternResolver;
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
        var kieHelper = new KieHelper();
        for (Resource resource : ruleResources()) {
            kieHelper.addContent(readRuleContent(resource), ResourceType.DRL);
        }
        return kieHelper.build().newKieSession();
    }

    private List<Resource> ruleResources() {
        try {
            var resources = resourcePatternResolver.getResources(RULE_LOCATION_PATTERN);
            var rules = Arrays.stream(resources)
                    .filter(Resource::exists)
                    .filter(this::isCategorizedRule)
                    .sorted(Comparator.comparing(this::resourceName))
                    .toList();
            if (rules.isEmpty()) {
                throw new IllegalStateException("未扫描到 Drools 规则文件: " + RULE_LOCATION_PATTERN);
            }
            return rules;
        } catch (IOException exception) {
            throw new IllegalStateException("扫描 Drools 规则文件失败: " + RULE_LOCATION_PATTERN, exception);
        }
    }

    private boolean isCategorizedRule(Resource resource) {
        var name = resourceName(resource).replace('\\', '/');
        return name.matches(".*[/!]rules/[^/]+/[^/]+\\.drl$")
                && !name.contains("/rules/fallback/");
    }

    private String readRuleContent(Resource resource) {
        try {
            var content = resource.getContentAsString(StandardCharsets.UTF_8);
            if (!StringUtils.hasText(content)) {
                throw new IllegalStateException("Drools 规则内容不能为空: " + resourceName(resource));
            }
            return content;
        } catch (IOException exception) {
            throw new IllegalStateException("Drools 规则文件加载失败: " + resourceName(resource), exception);
        }
    }

    private String resourceName(Resource resource) {
        try {
            return resource.getURL().toString();
        } catch (IOException exception) {
            return resource.getDescription();
        }
    }
}
