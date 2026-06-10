package com.wcdk.ai.aiagenttest.agent.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record OllamaMessage(
        String role,
        String content,
        String thinking
) {
    public OllamaMessage(String role, String content) {
        this(role, content, null);
    }
}
