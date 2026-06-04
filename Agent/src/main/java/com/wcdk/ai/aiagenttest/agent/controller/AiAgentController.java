package com.wcdk.ai.aiagenttest.agent.controller;

import java.nio.file.Path;

import com.wcdk.ai.aiagenttest.agent.core.AgentHealthResponse;
import com.wcdk.ai.aiagenttest.agent.core.ChatRequest;
import com.wcdk.ai.aiagenttest.agent.core.EdgeTtsService;
import com.wcdk.ai.aiagenttest.agent.core.SimpleAiAgent;
import com.wcdk.ai.aiagenttest.agent.core.TtsRequest;
import com.wcdk.ai.aiagenttest.agent.document.DocumentTrainingResponse;
import com.wcdk.ai.aiagenttest.agent.document.DocumentTrainingService;
import com.wcdk.ai.aiagenttest.agent.pipeline.Dl4jInferenceModule;
import com.wcdk.ai.aiagenttest.agent.pipeline.TrainingRequest;
import com.wcdk.ai.aiagenttest.agent.pipeline.TrainingResponse;
import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/agent")
@Tag(name = "AI Agent", description = "统一聊天入口，支持聊天、图片生成、意图训练与文档定制模型训练")
public class AiAgentController {

    private final SimpleAiAgent aiAgent;
    private final Dl4jInferenceModule inferenceModule;
    private final DocumentTrainingService documentTrainingService;
    private final EdgeTtsService edgeTtsService;
    private final WcdkProperties properties;

    public AiAgentController(
            SimpleAiAgent aiAgent,
            Dl4jInferenceModule inferenceModule,
            DocumentTrainingService documentTrainingService,
            EdgeTtsService edgeTtsService,
            WcdkProperties properties
    ) {
        this.aiAgent = aiAgent;
        this.inferenceModule = inferenceModule;
        this.documentTrainingService = documentTrainingService;
        this.edgeTtsService = edgeTtsService;
        this.properties = properties;
    }

    @GetMapping("/health")
    @Operation(summary = "检查服务健康状态", description = "返回当前 Agent 服务状态、默认模型名称和 Ollama 地址")
    public AgentHealthResponse health() {
        return aiAgent.health();
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "发送聊天消息", description = "统一聊天入口。系统会根据规则自动判断是走文本对话模型还是图片生成模型，并以 SSE 方式返回结果。")
    public ResponseEntity<SseEmitter> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache, no-transform")
                .body(aiAgent.chatStream(request));
    }

    @PostMapping(value = "/tts", produces = "audio/mpeg")
    @Operation(summary = "Text to speech", description = " Edge TTS 转MP3")
    public ResponseEntity<byte[]> synthesizeSpeech(@Valid @RequestBody TtsRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(edgeTtsService.synthesize(request));
    }

    @PostMapping("/train")
    @Operation(summary = "训练意图识别模型", description = "提交训练样本后重新训练 DL4J 意图模型，并导出训练结果。")
    public TrainingResponse train(@Valid @RequestBody TrainingRequest request) {
        var outputDirectory = request.outputDirectory() == null || request.outputDirectory().isBlank()
                ? Path.of(properties.getRules().getModel())
                : Path.of(request.outputDirectory());
        var epochs = request.epochs() == null ? 250 : request.epochs();
        return inferenceModule.train(request.samples(), epochs, outputDirectory);
    }

    @PostMapping(value = "/documents/train", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传资料训练模型", description = "接收文档后，抽取文本并生成训练数据")
    public DocumentTrainingResponse uploadDocumentAndTrain(@RequestPart("file") MultipartFile file) {
        return documentTrainingService.uploadAndTrain(file);
    }
}
