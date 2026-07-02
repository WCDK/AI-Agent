package com.wcdk.ai.agent.pipeline;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Component
public class PerceptionModule {

    public PerceptionResult perceive(String message) {
        var normalized = StringUtils.hasText(message) ? message.trim() : "";
        var lower = normalized.toLowerCase();
        var tokenCount = normalized.isBlank() ? 0 : normalized.split("\\s+").length;
        var question = normalized.endsWith("?")
                || lower.contains("what")
                || lower.contains("why")
                || lower.contains("how");
        var command = lower.contains("create")
                || lower.contains("implement")
                || lower.contains("write")
                || lower.contains("fix")
                || lower.contains("run");
        var risky = lower.contains("delete")
                || lower.contains("drop")
                || lower.contains("remove all");
        var image = lower.contains("image")
                || lower.contains("picture")
                || lower.contains("photo")
                || lower.contains("illustration")
                || lower.contains("draw")
                || lower.contains("paint");
        var chinese = normalized.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);

        return new PerceptionResult(normalized, tokenCount, question, command, image, risky, chinese);
    }
}
