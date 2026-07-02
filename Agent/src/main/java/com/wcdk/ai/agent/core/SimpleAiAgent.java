package com.wcdk.ai.agent.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.wcdk.ai.agent.document.KnowledgeBaseService;
import com.wcdk.ai.config.WcdkProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * @auther WCDK
 * @date 2026/7/2
 * @version 1.0
 **/
@Service
@Slf4j
public class SimpleAiAgent {

    private static final Pattern THINKING_BLOCK = Pattern.compile("(?is)<think>.*?</think>\\s*");

    private final WcdkProperties properties;
    private final OllamaChatClient ollamaChatClient;
    private final OllamaModelRouter ollamaModelRouter;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ConcurrentMap<String, List<OllamaMessage>> sessions = new ConcurrentHashMap<>();

    public SimpleAiAgent(
            WcdkProperties properties,
            OllamaChatClient ollamaChatClient,
            OllamaModelRouter ollamaModelRouter,
            SdWebuiClient sdWebuiClient,
            SdPromptEnhancer sdPromptEnhancer,
            com.wcdk.ai.agent.pipeline.AgentPipeline agentPipeline,
            KnowledgeBaseService knowledgeBaseService,
            EdgeTtsService edgeTtsService
    ) {
        this.properties = properties;
        this.ollamaChatClient = ollamaChatClient;
        this.ollamaModelRouter = ollamaModelRouter;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    public AgentHealthResponse health() {
        return new AgentHealthResponse(
                "UP",
                ollamaModelRouter.defaultTextModel(),
                properties.getAgent().getOllama().getBaseUrl()
        );
    }

    public SseEmitter chatStream(ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("消息内容不能为空。");
        }

        var sessionId = StringUtils.hasText(request.sessionId())
                ? request.sessionId()
                : UUID.randomUUID().toString();
        var history = sessions.computeIfAbsent(sessionId, ignored -> new ArrayList<>());
        var model = ollamaModelRouter.defaultTextModel();
        log.info("调用模型===={}===========",model);
        var timeoutMillis = Duration.ofSeconds(properties.getAgent().getOllama().getTimeoutSeconds()).toMillis();
        var emitter = new SseEmitter(timeoutMillis);

        synchronized (history) {
            history.add(new OllamaMessage("user", request.message().trim()));
            trimHistory(history);
        }

        CompletableFuture.runAsync(() -> streamTextResponse(emitter, sessionId, model, history, request.message()));
        return emitter;
    }

    public ChatResponse chatText(ChatRequest request) {
        if (request == null || !StringUtils.hasText(request.message())) {
            throw new IllegalArgumentException("消息内容不能为空。");
        }
        var sessionId = StringUtils.hasText(request.sessionId())
                ? request.sessionId()
                : UUID.randomUUID().toString();
        var history = sessions.computeIfAbsent(sessionId, ignored -> new ArrayList<>());
        var model = ollamaModelRouter.defaultTextModel();
        log.info("user:{}================调用模型===={}===========",request.message(),model);
        var messages = new ArrayList<OllamaMessage>();
        messages.add(new OllamaMessage("system", ragSystemPrompt(request.message())));

        synchronized (history) {
            history.add(new OllamaMessage("user", request.message().trim()));
            trimHistory(history);
            messages.addAll(history);
        }

        var answer = stripThinking(ollamaChatClient.chat(model, messages));
        synchronized (history) {
            history.add(new OllamaMessage("assistant", answer));
            trimHistory(history);
        }
        return new ChatResponse(sessionId, model, "chat", answer);
    }

    private void streamTextResponse(
            SseEmitter emitter,
            String sessionId,
            String model,
            List<OllamaMessage> history,
            String userMessage
    ) {
        var rawAnswer = new StringBuilder();
        var messages = new ArrayList<OllamaMessage>();
        messages.add(new OllamaMessage("system", ragSystemPrompt(userMessage)));
        synchronized (history) {
            messages.addAll(history);
        }

        try {
            sendEvent(emitter, "meta", new ChatStreamEvent("meta", sessionId, model, "chat", ""));
            ollamaChatClient.chatStream(model, messages, (eventType, content) -> {
                if (!StringUtils.hasText(content)) {
                    return;
                }
                rawAnswer.append(content);
                var type = "thinking".equals(eventType) ? "thinking" : "delta";
                sendEvent(emitter, type, new ChatStreamEvent(type, sessionId, model, "chat", content));
            });

            var answer = stripThinking(rawAnswer.toString());
            synchronized (history) {
                history.add(new OllamaMessage("assistant", answer));
                trimHistory(history);
            }
            sendEvent(emitter, "done", new ChatStreamEvent("done", sessionId, model, "chat", ""));
            emitter.complete();
        } catch (Exception exception) {
            try {
                sendEvent(emitter, "error", new ChatStreamEvent("error", sessionId, model, "chat", exception.getMessage()));
            } finally {
                emitter.completeWithError(exception);
            }
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, ChatStreamEvent event) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(event));
        } catch (Exception exception) {
            throw new IllegalStateException("发送流式事件失败。", exception);
        }
    }

    private void trimHistory(List<OllamaMessage> history) {
        var maxHistoryMessages = Math.max(2, properties.getAgent().getMaxHistoryMessages());
        while (history.size() > maxHistoryMessages) {
            history.removeFirst();
        }
    }

    private String stripThinking(String content) {
        return THINKING_BLOCK.matcher(content).replaceAll("").trim();
    }

    private String ragSystemPrompt(String userMessage) {
        var basePrompt = properties.getAgent().getSystemPrompt();
        var hits = knowledgeBaseService.search(userMessage);
        var context = knowledgeBaseService.buildContext(hits);
        if (!StringUtils.hasText(context)) {
            return basePrompt;
        }

        return basePrompt + "\n\n"
                + "回答时请优先使用以下知识库上下文。"
                + "如果上下文依据不足，请明确说明，不要编造事实。\n"
                + context;
    }
}
