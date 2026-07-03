package com.wcdk.ai.agent.core;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.wcdk.ai.agent.pipeline.PipelineResult;
import com.wcdk.ai.config.WcdkProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public class OllamaModelRouter {

    private final WcdkProperties properties;
    private final AtomicReference<String> currentModel = new AtomicReference<>("");

    public OllamaModelRouter(WcdkProperties properties) {
        this.properties = properties;
    }

    public String defaultTextModel() {
        var activeModel = currentModel();
        if (StringUtils.hasText(activeModel)) {
            return activeModel;
        }

        return configuredDefaultTextModel();
    }

    public String currentModel() {
        return currentModel.get();
    }

    public void switchCurrentModel(String model) {
        if (!StringUtils.hasText(model)) {
            throw new IllegalArgumentException("Ollama 模型名称不能为空。");
        }
        currentModel.set(model.trim());
    }

    private String configuredDefaultTextModel() {
        var defaultModel = properties.getAgent().getOllama().getDefaultModel();
        if (StringUtils.hasText(defaultModel)) {
            return defaultModel.trim();
        }
        return "deepseek-r1:7b";
    }

    public String resolve(PipelineResult pipelineResult) {
        var activeModel = currentModel();
        if (StringUtils.hasText(activeModel)) {
            return activeModel;
        }

        if (pipelineResult == null || pipelineResult.decision() == null) {
            return defaultTextModel();
        }

        var inference = pipelineResult.inference();
        if (inference != null
                && inference.confidence() >= properties.getAgent().getOllama().getIntentSwitchConfidence()) {
            var intentModel = findConfiguredModel(
                    properties.getAgent().getOllama().getIntentModels(),
                    inference.intent()
            );
            if (StringUtils.hasText(intentModel)) {
                return intentModel;
            }
        }

        var routeModel = findConfiguredModel(
                properties.getAgent().getOllama().getRouteModels(),
                pipelineResult.decision().modelRoute()
        );
        if (StringUtils.hasText(routeModel)) {
            return routeModel;
        }

        return defaultTextModel();
    }

    private String findConfiguredModel(Map<String, String> configuredModels, String key) {
        if (configuredModels == null || !StringUtils.hasText(key)) {
            return "";
        }
        var model = configuredModels.get(key.trim());
        return StringUtils.hasText(model) ? model.trim() : "";
    }

}
