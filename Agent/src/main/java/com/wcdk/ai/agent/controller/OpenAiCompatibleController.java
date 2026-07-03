package com.wcdk.ai.agent.controller;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.wcdk.ai.agent.core.ChatRequest;
import com.wcdk.ai.agent.core.ChatResponse;
import com.wcdk.ai.agent.core.OllamaModelRouter;
import com.wcdk.ai.config.WcdkProperties;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/v1")
public class OpenAiCompatibleController {

    private static final String DEFAULT_ROLE = "assistant";

    private final WcdkProperties properties;
    private final OllamaModelRouter ollamaModelRouter;
    private final AiAgentController aiAgentController;

    public OpenAiCompatibleController(
            WcdkProperties properties,
            OllamaModelRouter ollamaModelRouter,
            AiAgentController aiAgentController
    ) {
        this.properties = properties;
        this.ollamaModelRouter = ollamaModelRouter;
        this.aiAgentController = aiAgentController;
    }

//    @GetMapping("/model")
//    public ModelsResponse models() {
//        var data = configuredModels().stream()
//                .filter(StringUtils::hasText)
//                .map(model -> new ModelData(model, "model", "ollama"))
//                .toList();
//        return new ModelsResponse("list", data);
//    }

    @PostMapping("/chat/completions")
    public ResponseEntity<?> chatCompletions(@Valid @RequestBody ChatCompletionRequest request) {
        var chatResponse = aiAgentController.chatText(toChatRequest(request));
        if (request.stream()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .cacheControl(CacheControl.noStore())
                    .header("X-Accel-Buffering", "no")
                    .header("Cache-Control", "no-cache, no-transform")
                    .body(streamCompletionResponse(chatResponse));
        }
        return ResponseEntity.ok(completionResponse(chatResponse));
    }

    private ChatRequest toChatRequest(ChatCompletionRequest request) {
        return new ChatRequest(null, extractUserMessage(request.messages()));
    }

    private String extractUserMessage(List<OpenAiMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages 不能为空。");
        }

        for (int index = messages.size() - 1; index >= 0; index--) {
            var message = messages.get(index);
            if (message == null || !StringUtils.hasText(message.role())) {
                continue;
            }
            if (!"user".equalsIgnoreCase(message.role().trim())) {
                continue;
            }
            var content = extractTextContent(message.content());
            if (StringUtils.hasText(content)) {
                return content;
            }
        }

        for (var message : messages) {
            if (message == null) {
                continue;
            }
            var content = extractTextContent(message.content());
            if (StringUtils.hasText(content)) {
                return content;
            }
        }
        throw new IllegalArgumentException("messages 必须包含文本内容。");
    }

    private String extractTextContent(JsonNode content) {
        if (content == null || content.isNull()) {
            return "";
        }
        if (content.isTextual()) {
            return content.asText();
        }
        if (!content.isArray()) {
            return content.asText("");
        }

        var text = new StringBuilder();
        for (var item : content) {
            var type = item.path("type").asText();
            if ("text".equals(type)) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(item.path("text").asText());
            }
        }
        return text.toString();
    }


    private ChatCompletionResponse completionResponse(ChatResponse chatResponse) {
        var now = Instant.now().getEpochSecond();
        var message = new ChatMessage(DEFAULT_ROLE, chatResponse.answer());
        var choice = new ChatChoice(0, message, null, "stop");
        return new ChatCompletionResponse(
                "chatcmpl-" + UUID.randomUUID(),
                "chat.completion",
                now,
                chatResponse.model(),
                List.of(choice),
                Map.of()
        );
    }

    private SseEmitter streamCompletionResponse(ChatResponse chatResponse) {
        var emitter = new SseEmitter(properties.getAgent().getOllama().getTimeoutSeconds() * 1000);
        Thread.startVirtualThread(() -> {
            try {
                var now = Instant.now().getEpochSecond();
                var delta = new ChatDelta(DEFAULT_ROLE, chatResponse.answer());
                var choice = new StreamChoice(0, delta, null);
                var chunk = new ChatCompletionChunk(
                        "chatcmpl-" + UUID.randomUUID(),
                        "chat.completion.chunk",
                        now,
                        chatResponse.model(),
                        List.of(choice)
                );
                emitter.send(SseEmitter.event().data(chunk));
                emitter.send(SseEmitter.event().data("[DONE]"));
                emitter.complete();
            } catch (Exception exception) {
                emitter.completeWithError(exception);
            }
        });
        return emitter;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ChatCompletionRequest(
            String model,
            List<OpenAiMessage> messages,
            boolean stream
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record OpenAiMessage(
            String role,
            JsonNode content
    ) {
    }

    private record ModelsResponse(
            String object,
            List<ModelData> data
    ) {
    }

    private record ModelData(
            String id,
            String object,
            @JsonProperty("owned_by") String ownedBy
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionResponse(
            String id,
            String object,
            long created,
            String model,
            List<ChatChoice> choices,
            Map<String, Object> usage
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatChoice(
            int index,
            ChatMessage message,
            Object logprobs,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    private record ChatMessage(
            String role,
            String content
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatCompletionChunk(
            String id,
            String object,
            long created,
            String model,
            List<StreamChoice> choices
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record StreamChoice(
            int index,
            ChatDelta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ChatDelta(
            String role,
            String content
    ) {
    }
}
