package com.wcdk.ai.agent.core;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.BiConsumer;

import com.wcdk.ai.config.WcdkProperties;
import org.springframework.stereotype.Component;

@Component
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public class OllamaChatClient {

    private final WcdkProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaChatClient(WcdkProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getAgent().getOllama().getTimeoutSeconds()))
                .build();
    }

    public String chat(String model, List<OllamaMessage> messages) {
        try {
            var requestBody = new OllamaChatRequest(model, false, messages);
            var request = HttpRequest.newBuilder()
                    .uri(chatUri())
                    .timeout(Duration.ofSeconds(properties.getAgent().getOllama().getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama request failed: HTTP " + response.statusCode() + " - " + response.body());
            }

            var chatResponse = objectMapper.readValue(response.body(), OllamaChatResponse.class);
            if (chatResponse.message() == null || chatResponse.message().content() == null) {
                throw new IllegalStateException("Ollama response does not contain message content.");
            }
            return combineThinkingAndContent(chatResponse.message());
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to call Ollama. Confirm Ollama is running and the model is pulled.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama request was interrupted.", exception);
        }
    }

    public void chatStream(String model, List<OllamaMessage> messages, BiConsumer<String, String> chunkConsumer) {
        try {
            var requestBody = new OllamaChatRequest(model, true, messages);
            var request = HttpRequest.newBuilder()
                    .uri(chatUri())
                    .timeout(Duration.ofSeconds(properties.getAgent().getOllama().getTimeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .build();

            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama stream request failed: HTTP " + response.statusCode());
            }

            try (var lines = response.body()) {
                var iterator = lines.iterator();
                while (iterator.hasNext()) {
                    var line = iterator.next();
                    if (line == null || line.isBlank()) {
                        continue;
                    }

                    var chatResponse = objectMapper.readValue(line, OllamaChatResponse.class);
                    if (chatResponse.message() != null) {
                        if (chatResponse.message().thinking() != null) {
                            chunkConsumer.accept("thinking", chatResponse.message().thinking());
                        }
                        if (chatResponse.message().content() != null) {
                            chunkConsumer.accept("delta", chatResponse.message().content());
                        }
                    }
                    if (chatResponse.done()) {
                        break;
                    }
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to stream from Ollama. Confirm Ollama is running and the model is pulled.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Ollama stream request was interrupted.", exception);
        }
    }

    private URI chatUri() {
        var baseUrl = properties.getAgent().getOllama().getBaseUrl().replaceAll("/+$", "");
        return URI.create(baseUrl + "/api/chat");
    }

    private String combineThinkingAndContent(OllamaMessage message) {
        var content = message.content() == null ? "" : message.content();
        if (message.thinking() == null || message.thinking().isBlank()) {
            return content;
        }
        return "<think>\n" + message.thinking() + "\n</think>\n" + content;
    }

    private record OllamaChatRequest(
            String model,
            boolean stream,
            List<OllamaMessage> messages
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OllamaChatResponse(
            OllamaMessage message,
            boolean done
    ) {
    }
}
