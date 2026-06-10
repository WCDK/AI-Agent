package com.wcdk.ai.aiagenttest.agent.pipeline;

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
                || normalized.endsWith("？")
                || lower.contains("what")
                || lower.contains("why")
                || lower.contains("how")
                || normalized.contains("什么")
                || normalized.contains("为什么")
                || normalized.contains("怎么");
        var command = lower.contains("create")
                || lower.contains("implement")
                || lower.contains("write")
                || lower.contains("fix")
                || lower.contains("run")
                || normalized.contains("创建")
                || normalized.contains("实现")
                || normalized.contains("修复")
                || normalized.contains("执行");
        var risky = lower.contains("delete")
                || lower.contains("drop")
                || lower.contains("remove all")
                || normalized.contains("删除")
                || normalized.contains("清空");
        var image = lower.contains("image")
                || lower.contains("picture")
                || lower.contains("draw")
                || lower.contains("generate")
                || normalized.contains("图片")
                || normalized.contains("图像")
                || normalized.contains("画")
                || normalized.contains("生成图");
        var chinese = normalized.codePoints().anyMatch(codePoint ->
                Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);

        return new PerceptionResult(normalized, tokenCount, question, command, image, risky, chinese);
    }
}
