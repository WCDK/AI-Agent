package com.wcdk.ai.agent.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcdk.ai.config.WcdkProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
/**
 * @auther WCDK
 * @date 2026/7/2
 * @version 1.0
 **/
public class SdWebuiClient {

    private final WcdkProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public SdWebuiClient(WcdkProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getAgent().getSdWebui().getTimeoutSeconds()))
                .build();
    }

    public List<GeneratedImage> txt2img(String prompt) {
        return txt2img(prompt, "");
    }

    public List<GeneratedImage> txt2img(String prompt, String negativePrompt) {
        if (!properties.getAgent().getSdWebui().isEnabled()) {
            throw new IllegalStateException("Stable Diffusion WebUI 图像生成未启用。");
        }
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("图像提示词不能为空。");
        }

        try {
            var settings = properties.getAgent().getSdWebui();
            var effectiveNegativePrompt = StringUtils.hasText(negativePrompt)
                    ? negativePrompt.trim()
                    : settings.getNegativePrompt();
            var requestBody = new Txt2ImgRequest(
                    prompt.trim(),
                    effectiveNegativePrompt,
                    settings.getSteps(),
                    settings.getWidth(),
                    settings.getHeight(),
                    settings.getCfgScale(),
                    Math.max(1, settings.getBatchSize()),
                    settings.getSamplerName()
            );
            var request = HttpRequest.newBuilder()
                    .uri(txt2imgUri())
                    .timeout(Duration.ofSeconds(settings.getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Stable Diffusion WebUI 请求失败：HTTP "
                        + response.statusCode() + " - " + response.body());
            }

            var txt2ImgResponse = objectMapper.readValue(response.body(), Txt2ImgResponse.class);
            if (txt2ImgResponse.images() == null || txt2ImgResponse.images().isEmpty()) {
                throw new IllegalStateException("Stable Diffusion WebUI 响应中没有图片。");
            }

            return txt2ImgResponse.images().stream()
                    .filter(StringUtils::hasText)
                    .map(SdWebuiClient::stripDataUrlPrefix)
                    .map(image -> new GeneratedImage(image, prompt.trim()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("调用 Stable Diffusion WebUI 失败，请确认 "
                    + properties.getAgent().getSdWebui().getWebuiDirectory()
                    + " 已使用 --api 启动，地址为 "
                    + properties.getAgent().getSdWebui().getBaseUrl() + ".", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stable Diffusion WebUI 请求被中断。", exception);
        }
    }

    private URI txt2imgUri() {
        var baseUrl = properties.getAgent().getSdWebui().getBaseUrl().replaceAll("/+$", "");
        return URI.create(baseUrl + "/sdapi/v1/txt2img");
    }

    private static String stripDataUrlPrefix(String image) {
        var commaIndex = image.indexOf(',');
        if (image.startsWith("data:image/") && commaIndex >= 0) {
            return image.substring(commaIndex + 1);
        }
        return image;
    }

    private record Txt2ImgRequest(
            String prompt,
            @JsonProperty("negative_prompt")
            String negativePrompt,
            int steps,
            int width,
            int height,
            @JsonProperty("cfg_scale")
            double cfgScale,
            @JsonProperty("batch_size")
            int batchSize,
            @JsonProperty("sampler_name")
            String samplerName
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record Txt2ImgResponse(List<String> images) {
    }
}
