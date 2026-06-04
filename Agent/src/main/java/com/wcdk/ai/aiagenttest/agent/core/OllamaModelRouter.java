package com.wcdk.ai.aiagenttest.agent.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import com.wcdk.ai.aiagenttest.agent.pipeline.PipelineResult;
import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
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
            throw new IllegalArgumentException("Ollama model name must not be blank.");
        }
        currentModel.set(model.trim());
    }

    private String configuredDefaultTextModel() {
        var ollama = properties.getAgent().getOllama();
        var models = ollama.getModels();
        if (models != null) {
            var configuredModel = models.stream()
                    .filter(StringUtils::hasText)
                    .findFirst()
                    .orElse("");
            if (StringUtils.hasText(configuredModel)) {
                return configuredModel;
            }
        }
        if (StringUtils.hasText(ollama.getModel())) {
            return ollama.getModel();
        }
        return "";
    }

    public String resolve(PipelineResult pipelineResult) {
        var activeModel = currentModel();
        if (StringUtils.hasText(activeModel)) {
            return activeModel;
        }

        var route = pipelineResult.decision().modelRoute();

        var inference = pipelineResult.inference();
        if (inference.confidence() >= properties.getAgent().getOllama().getIntentSwitchConfidence()) {
            var intentModel = findConfiguredModel(properties.getAgent().getOllama().getIntentModels(), inference.intent());
            if (StringUtils.hasText(intentModel)) {
                return intentModel;
            }
        }

        var routeModel = findConfiguredModel(properties.getAgent().getOllama().getRouteModels(), route);
        if (StringUtils.hasText(routeModel)) {
            return routeModel;
        }

        return defaultTextModel();
    }

    private String findConfiguredModel(Map<String, String> configuredModels, String key) {
        if (configuredModels == null || !StringUtils.hasText(key)) {
            return "";
        }
        var model = configuredModels.get(key);
        if (!isAllowedModel(model)) {
            return "";
        }
        return model;
    }

    private boolean isAllowedModel(String model) {
        if (!StringUtils.hasText(model)) {
            return false;
        }

        List<String> models = properties.getAgent().getOllama().getModels();
        return models == null || models.isEmpty() || models.contains(model);
    }
}
