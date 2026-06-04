package com.wcdk.ai.aiagenttest.agent.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OllamaMessage(
        String role,
        String content,
        String thinking
) {
    public OllamaMessage(String role, String content) {
        this(role, content, null);
    }
}
