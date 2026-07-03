package com.wcdk.ai.agent.core;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.wcdk.ai.agent.rules.DecisionResult;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Builds Stable Diffusion WebUI prompts from the user's own image description.
 */
@Component
public class SdPromptEnhancer {

    private static final Pattern EN_IMAGE_REQUEST_PREFIX = Pattern.compile(
            "(?iu)^\\s*(please\\s+)?(help\\s+me\\s+)?(generate|create|draw|make|paint)\\s+"
                    + "(an?\\s+|the\\s+)?(image|picture|photo|illustration)?\\s*(of\\s+)?"
    );
    private static final Pattern ZH_IMAGE_REQUEST_PREFIX = Pattern.compile(
            "^\\s*(\\u8bf7|\\u5e2e\\u6211|\\u7ed9\\u6211|\\u9ebb\\u70e6)?\\s*"
                    + "(\\u751f\\u6210|\\u753b|\\u7ed8\\u5236|\\u5236\\u4f5c|\\u521b\\u4f5c)\\s*"
                    + "(\\u4e00\\u5f20|\\u4e00\\u4e2a|\\u4e00\\u5e45)?\\s*"
                    + "(\\u56fe\\u7247|\\u56fe\\u50cf|\\u7167\\u7247|\\u63d2\\u753b|\\u753b)?\\s*"
                    + "(\\u5173\\u4e8e|\\u6709\\u5173|\\u8868\\u73b0|\\u63cf\\u7ed8)?\\s*"
    );

    private static final List<String> BASE_POSITIVE_KEYWORDS = List.of(
            "masterpiece",
            "best quality",
            "high quality",
            "ultra detailed",
            "photorealistic",
            "realistic"
    );
    private static final List<String> DEFAULT_LORAS = List.of(
            "<lora:add_detail:0.7>",
            "<lora:epi_noiseoffset2:0.6>"
    );
    private static final List<String> BASE_NEGATIVE_KEYWORDS = List.of(
            "worst quality",
            "low quality",
            "lowres",
            "blurry",
            "jpeg artifacts",
            "noisy image",
            "overexposed",
            "underexposed",
            "bad anatomy",
            "deformed",
            "mutated",
            "extra limbs",
            "missing limbs",
            "text",
            "watermark",
            "logo",
            "signature",
            "frame",
            "cropped",
            "out of frame",
            "poor composition"
    );
    private static final List<String> HUMAN_NEGATIVE_KEYWORDS = List.of(
            "bad hands",
            "extra fingers",
            "missing fingers",
            "deformed fingers",
            "bad face",
            "asymmetrical eyes"
    );
    private static final List<String> ANIMAL_NEGATIVE_KEYWORDS = List.of(
            "malformed paws",
            "extra legs",
            "broken tail",
            "twisted body",
            "duplicate animal"
    );

    public SdPrompt enhance(String userMessage, DecisionResult decision) {
        var subject = extractSubject(userMessage);
        var positiveKeywords = new ArrayList<String>();
        positiveKeywords.addAll(BASE_POSITIVE_KEYWORDS);
        positiveKeywords.addAll(resolveLoras(decision));
        positiveKeywords.add(StringUtils.hasText(subject) ? subject : "beautiful subject");
        positiveKeywords.addAll(inferPositiveKeywords(subject));
        positiveKeywords.addAll(List.of(
                "detailed texture",
                "natural color grading",
                "balanced composition"
        ));

        var negativeKeywords = new ArrayList<String>();
        if (decision != null && StringUtils.hasText(decision.negativePrompt())) {
            negativeKeywords.addAll(splitKeywords(decision.negativePrompt()));
        }
        negativeKeywords.addAll(BASE_NEGATIVE_KEYWORDS);
        negativeKeywords.addAll(inferNegativeKeywords(subject));

        return new SdPrompt(joinUnique(positiveKeywords), joinUnique(negativeKeywords));
    }

    public String extractSubject(String userMessage) {
        var source = StringUtils.hasText(userMessage) ? userMessage.trim() : "";
        var prompt = EN_IMAGE_REQUEST_PREFIX.matcher(source).replaceFirst("").trim();
        prompt = ZH_IMAGE_REQUEST_PREFIX.matcher(prompt).replaceFirst("").trim();
        prompt = prompt
                .replaceAll("^[,，。！？、；;\\s]+", "")
                .replaceAll("[。；;\\s]*(谢谢|多谢|thanks|thank you)[。!！?？\\s]*$", "")
                .trim();
        return StringUtils.hasText(prompt) ? prompt : source;
    }

    private List<String> resolveLoras(DecisionResult decision) {
        var loras = new ArrayList<String>();
        if (decision != null && StringUtils.hasText(decision.loraSetting())) {
            loras.addAll(splitKeywords(decision.loraSetting()));
        }
        for (String defaultLora : DEFAULT_LORAS) {
            if (!containsLora(loras, loraName(defaultLora))) {
                loras.add(defaultLora);
            }
        }
        return loras;
    }

    private List<String> inferPositiveKeywords(String subject) {
        var keywords = new ArrayList<String>();
        if (!StringUtils.hasText(subject)) {
            return keywords;
        }

        if (containsAny(subject, "cat", "\u732b")) {
            keywords.add("cat");
            keywords.add("detailed fur");
        }
        if (containsAny(subject, "orange", "tabby", "\u6a58\u732b", "\u6854\u732b")) {
            keywords.add("orange tabby fur");
        }
        if (containsAny(subject, "dog", "\u72d7")) {
            keywords.add("dog");
            keywords.add("detailed fur");
        }
        if (containsAny(subject, "person", "portrait", "girl", "boy", "man", "woman",
                "\u4eba", "\u4eba\u50cf", "\u5973\u5b69", "\u7537\u5b69")) {
            keywords.add("natural portrait");
            keywords.add("realistic skin texture");
        }
        if (containsAny(subject, "sleep", "sleeping", "\u7761", "\u7761\u89c9")) {
            keywords.add("sleeping peacefully");
            keywords.add("closed eyes");
            keywords.add("relaxed pose");
        }
        if (containsAny(subject, "lazy", "\u61d2", "\u6175\u61d2")) {
            keywords.add("lazy mood");
            keywords.add("peaceful atmosphere");
        }
        if (containsAny(subject, "tree shade", "shade", "under a tree", "\u6811\u836b", "\u6811\u9634")) {
            keywords.add("under the shade of a tree");
            keywords.add("dappled sunlight");
            keywords.add("soft shadows");
        }
        if (containsAny(subject, "summer", "\u590f\u65e5", "\u590f\u5929")) {
            keywords.add("warm summer atmosphere");
            keywords.add("lush green foliage");
        }
        if (containsAny(subject, "noon", "midday", "\u6b63\u5348", "\u4e2d\u5348")) {
            keywords.add("bright midday light");
        }
        if (containsAny(subject, "night", "\u591c\u665a", "\u591c\u8272")) {
            keywords.add("night lighting");
            keywords.add("low light atmosphere");
        }
        if (containsAny(subject, "rain", "rainy", "\u96e8", "\u4e0b\u96e8")) {
            keywords.add("rainy atmosphere");
            keywords.add("wet surface reflections");
        }
        if (containsAny(subject, "snow", "snowy", "\u96ea", "\u4e0b\u96ea")) {
            keywords.add("snowy atmosphere");
            keywords.add("soft winter light");
        }
        if (containsAny(subject, "garden", "\u82b1\u56ed")) {
            keywords.add("outdoor garden");
        }
        if (containsAny(subject, "grass", "\u8349\u5730")) {
            keywords.add("grass");
        }
        return keywords;
    }

    private List<String> inferNegativeKeywords(String subject) {
        var keywords = new ArrayList<String>();
        if (!StringUtils.hasText(subject)) {
            return keywords;
        }
        if (containsAny(subject, "cat", "dog", "\u732b", "\u72d7")) {
            keywords.addAll(ANIMAL_NEGATIVE_KEYWORDS);
        }
        if (containsAny(subject, "person", "portrait", "girl", "boy", "man", "woman",
                "\u4eba", "\u4eba\u50cf", "\u5973\u5b69", "\u7537\u5b69")) {
            keywords.addAll(HUMAN_NEGATIVE_KEYWORDS);
        }
        if (containsAny(subject, "sleep", "sleeping", "\u7761", "\u7761\u89c9")) {
            keywords.add("open eyes");
            keywords.add("aggressive expression");
        }
        return keywords;
    }

    private boolean containsAny(String source, String... candidates) {
        if (!StringUtils.hasText(source)) {
            return false;
        }
        var lower = source.toLowerCase();
        for (String candidate : candidates) {
            if (lower.contains(candidate.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean containsLora(List<String> loras, String loraName) {
        return loras.stream().map(this::loraName).anyMatch(loraName::equals);
    }

    private String loraName(String lora) {
        var matcher = Pattern.compile("<lora:([^:>]+):[^>]+>").matcher(lora.trim());
        return matcher.matches() ? matcher.group(1) : lora.trim();
    }

    private List<String> splitKeywords(String prompt) {
        return Pattern.compile("\\s*,\\s*")
                .splitAsStream(prompt.trim())
                .filter(StringUtils::hasText)
                .toList();
    }

    private String joinUnique(List<String> keywords) {
        Set<String> unique = new LinkedHashSet<>();
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword)) {
                unique.add(keyword.trim());
            }
        }
        return String.join(", ", unique);
    }
}
