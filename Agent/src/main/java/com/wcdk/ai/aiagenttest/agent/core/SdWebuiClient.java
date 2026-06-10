package com.wcdk.ai.aiagenttest.agent.core;

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
import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
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
        if (!properties.getAgent().getSdWebui().isEnabled()) {
            throw new IllegalStateException("Stable Diffusion WebUI image generation is disabled.");
        }
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalArgumentException("Image prompt must not be blank.");
        }

        try {
            var settings = properties.getAgent().getSdWebui();
            var requestBody = new Txt2ImgRequest(
                    prompt.trim(),
                    settings.getNegativePrompt(),
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
                throw new IllegalStateException("Stable Diffusion WebUI request failed: HTTP "
                        + response.statusCode() + " - " + response.body());
            }

            var txt2ImgResponse = objectMapper.readValue(response.body(), Txt2ImgResponse.class);
            if (txt2ImgResponse.images() == null || txt2ImgResponse.images().isEmpty()) {
                throw new IllegalStateException("Stable Diffusion WebUI response does not contain images.");
            }

            return txt2ImgResponse.images().stream()
                    .filter(StringUtils::hasText)
                    .map(SdWebuiClient::stripDataUrlPrefix)
                    .map(image -> new GeneratedImage(image, prompt.trim()))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call Stable Diffusion WebUI. Confirm "
                    + properties.getAgent().getSdWebui().getWebuiDirectory()
                    + " is running with --api at "
                    + properties.getAgent().getSdWebui().getBaseUrl() + ".", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Stable Diffusion WebUI request was interrupted.", exception);
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
