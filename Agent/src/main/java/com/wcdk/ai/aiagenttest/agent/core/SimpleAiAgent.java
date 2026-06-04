package com.wcdk.ai.aiagenttest.agent.core;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import com.wcdk.ai.aiagenttest.agent.document.KnowledgeBaseService;
import com.wcdk.ai.aiagenttest.agent.pipeline.AgentPipeline;
import com.wcdk.ai.aiagenttest.agent.pipeline.PipelineResult;
import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
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
            AgentPipeline agentPipeline,
            KnowledgeBaseService knowledgeBaseService
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
        PipelineResult pipelineResult;
        synchronized (history) {
            history.add(new OllamaMessage("user", request.message().trim()));
            trimHistory(history);
            // 先根据用户问题检索资料库，再用增强后的系统提示词构造模型消息。
            pipelineResult = agentPipeline.prepare(request.message(), ragSystemPrompt(request.message()), new ArrayList<>(history));
        }

        var timeoutMillis = Duration.ofSeconds(properties.getAgent().getOllama().getTimeoutSeconds()).toMillis();
        var emitter = new SseEmitter(timeoutMillis);
        CompletableFuture.runAsync(() -> streamTextResponse(emitter, sessionId, request.message(), history, pipelineResult));
        return emitter;
    }

    private void streamTextResponse(
            SseEmitter emitter,
            String sessionId,
            String userMessage,
            List<OllamaMessage> history,
            PipelineResult pipelineResult
    ) {
        var rawAnswer = new StringBuilder();
        var model = ollamaModelRouter.resolve(pipelineResult);
        var modelRoute = pipelineResult.decision().modelRoute();

        try {
            sendEvent(emitter, "meta", new ChatStreamEvent("meta", sessionId, model, modelRoute, pipelineResult.traceSummary()));
            ollamaChatClient.chatStream(model, pipelineResult.messages(), (eventType, content) -> {
                if ("thinking".equals(eventType)) {
                    rawAnswer.append("<think>").append(content).append("</think>");
                    sendEvent(emitter, "thinking", new ChatStreamEvent("thinking", sessionId, model, modelRoute, content));
                    return;
                }

                rawAnswer.append(content);
                sendEvent(emitter, "delta", new ChatStreamEvent("delta", sessionId, model, modelRoute, content));
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

    /**
     * 检索增强生成的回答阶段：先用用户问题检索资料库，再把命中的资料片段注入系统提示词。
     * 模型仍然走原有聊天接口，但回答时会优先依据检索上下文，避免只依赖模型自身记忆。
     */
    private String ragSystemPrompt(String userMessage) {
        var basePrompt = properties.getAgent().getSystemPrompt();
        var hits = knowledgeBaseService.search(userMessage);
        var context = knowledgeBaseService.buildContext(hits);
        if (!StringUtils.hasText(context)) {
            return basePrompt;
        }

        return basePrompt + "\n\n"
                + "请优先结合以下资料库检索结果回答用户问题。"
                + "如果资料库没有足够依据，请明确说明资料不足，不要编造。\n"
                + context;
    }
}
