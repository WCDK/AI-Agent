package com.wcdk.ai.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Data
@ConfigurationProperties(prefix = "wcdk")
public class WcdkProperties {

    private final Agent agent = new Agent();
    private final Rules rules = new Rules();
    private final Document document = new Document();

    @Data
    public static class Agent {
        private final Ollama ollama = new Ollama();
        private final SdWebui sdWebui = new SdWebui();
        private final Tts tts = new Tts();
        private String systemPrompt = "你是一个简洁、可靠的 AI 助手。优先使用中文回答，回答要直接、准确、可执行。";
        private int maxHistoryMessages = 12;
    }

    @Data
    public static class Ollama {
        private String baseUrl;
        private String defaultModel;
        private List<String> models = new ArrayList<>();
        private Map<String, String> routeModels = new LinkedHashMap<>();
        private Map<String, String> intentModels = new LinkedHashMap<>();
        private double intentSwitchConfidence = 0.6D;
        private long timeoutSeconds = 120;
        private String trainingModelPrefix;
    }

    @Data
    public static class Tts {
        private String command;
        private String voice;
        private int timeoutSeconds = 30;
        private int maxTextLength = 2000;
    }

    @Data
    public static class SdWebui {
        private boolean enabled = true;
        private String baseUrl;
        private String webuiDirectory;
        private long timeoutSeconds = 300;
        private int width = 512;
        private int height = 512;
        private int steps = 20;
        private double cfgScale = 7.0D;
        private int batchSize = 1;
        private String negativePrompt = "low quality, blurry, distorted, watermark, text";
        private String samplerName = "DPM++ 2M Karras";
    }

    @Data
    public static class Rules {
        private String model = "/Dl4jSource";
    }

    @Data
    public static class Document {
        private String sourceDirectory;
        private int chunkSize = 900;
        private int chunkOverlap = 120;
    }
}
