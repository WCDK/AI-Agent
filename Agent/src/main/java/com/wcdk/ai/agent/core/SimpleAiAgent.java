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
import com.wcdk.ai.agent.pipeline.AgentPipeline;
import com.wcdk.ai.agent.pipeline.PipelineResult;
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
    private final AgentPipeline agentPipeline;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ConcurrentMap<String, List<OllamaMessage>> sessions = new ConcurrentHashMap<>();

    public SimpleAiAgent(
            WcdkProperties properties,
            OllamaChatClient ollamaChatClient,
            OllamaModelRouter ollamaModelRouter,
            SdWebuiClient sdWebuiClient,
            SdPromptEnhancer sdPromptEnhancer,
            AgentPipeline agentPipeline,
            KnowledgeBaseService knowledgeBaseService,
            EdgeTtsService edgeTtsService
    ) {
        this.properties = properties;
        this.ollamaChatClient = ollamaChatClient;
        this.ollamaModelRouter = ollamaModelRouter;
        this.agentPipeline = agentPipeline;
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
        var timeoutMillis = Duration.ofSeconds(properties.getAgent().getOllama().getTimeoutSeconds()).toMillis();
        var emitter = new SseEmitter(timeoutMillis);
        PipelineResult pipelineResult;

        synchronized (history) {
            history.add(new OllamaMessage("user", request.message().trim()));
            trimHistory(history);
            pipelineResult = agentPipeline.prepare(request.message(), ragSystemPrompt(request.message()), new ArrayList<>(history));
        }

        CompletableFuture.runAsync(() -> streamTextResponse(emitter, sessionId, history, request.message(), pipelineResult));
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
        PipelineResult pipelineResult;

        synchronized (history) {
            history.add(new OllamaMessage("user", request.message().trim()));
            trimHistory(history);
            pipelineResult = agentPipeline.prepare(request.message(), ragSystemPrompt(request.message()), new ArrayList<>(history));
        }

        var model = ollamaModelRouter.resolve(pipelineResult);
        log.info("用户输入：{}，识别意图：{}，模型路由：{}，调用模型：{}",
                request.message().substring(0,request.message().indexOf("\n")),
                pipelineResult.inference().intent(),
                pipelineResult.decision().modelRoute(),
                model);
        var answer = stripThinking(ollamaChatClient.chat(model, pipelineResult.messages()));
        synchronized (history) {
            history.add(new OllamaMessage("assistant", answer));
            trimHistory(history);
        }
        agentPipeline.learn(sessionId, request.message(), pipelineResult.decision(), answer);
        return new ChatResponse(sessionId, model, pipelineResult.decision().modelRoute(), answer);
    }

    private void streamTextResponse(
            SseEmitter emitter,
            String sessionId,
            List<OllamaMessage> history,
            String userMessage,
            PipelineResult pipelineResult
    ) {
        var rawAnswer = new StringBuilder();
        var model = ollamaModelRouter.resolve(pipelineResult);
        var modelRoute = pipelineResult.decision().modelRoute();
        log.info("用户输入：{}，识别意图：{}，模型路由：{}，调用模型：{}",
                userMessage.substring(0,userMessage.indexOf("\n")),
                pipelineResult.inference().intent(),
                modelRoute,
                model);

        try {
            sendEvent(emitter, "meta", new ChatStreamEvent("meta", sessionId, model, modelRoute, pipelineResult.traceSummary()));
            ollamaChatClient.chatStream(model, pipelineResult.messages(), (eventType, content) -> {
                if (!StringUtils.hasText(content)) {
                    return;
                }
                rawAnswer.append(content);
                var type = "thinking".equals(eventType) ? "thinking" : "delta";
                sendEvent(emitter, type, new ChatStreamEvent(type, sessionId, model, modelRoute, content));
            });

            var answer = stripThinking(rawAnswer.toString());
            synchronized (history) {
                history.add(new OllamaMessage("assistant", answer));
                trimHistory(history);
            }
            agentPipeline.learn(sessionId, userMessage, pipelineResult.decision(), answer);
            sendEvent(emitter, "done", new ChatStreamEvent("done", sessionId, model, modelRoute, ""));
            emitter.complete();
        } catch (Exception exception) {
            try {
                sendEvent(emitter, "error", new ChatStreamEvent("error", sessionId, model, modelRoute, exception.getMessage()));
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
