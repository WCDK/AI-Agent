package com.wcdk.ai.agent.controller;

import java.nio.file.Path;

import com.wcdk.ai.agent.core.AgentHealthResponse;
import com.wcdk.ai.agent.core.ChatRequest;
import com.wcdk.ai.agent.core.ChatResponse;
import com.wcdk.ai.agent.core.EdgeTtsService;
import com.wcdk.ai.agent.core.GeneratedImage;
import com.wcdk.ai.agent.core.SdWebuiClient;
import com.wcdk.ai.agent.core.SimpleAiAgent;
import com.wcdk.ai.agent.core.TtsRequest;
import com.wcdk.ai.agent.core.Txt2ImgRequest;
import com.wcdk.ai.agent.document.DocumentTrainingResponse;
import com.wcdk.ai.agent.document.DocumentTrainingService;
import com.wcdk.ai.agent.pipeline.Dl4jInferenceModule;
import com.wcdk.ai.agent.pipeline.TrainingRequest;
import com.wcdk.ai.agent.pipeline.TrainingResponse;
import com.wcdk.ai.config.WcdkProperties;
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
@Tag(name = "AI Agent", description = "一体化AI服务接口：智能对话、图像生成、意图识别与文档训练")
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public class AiAgentController {

    private final SimpleAiAgent aiAgent;
    private final SdWebuiClient sdWebuiClient;
    private final Dl4jInferenceModule inferenceModule;
    private final DocumentTrainingService documentTrainingService;
    private final EdgeTtsService edgeTtsService;
    private final WcdkProperties properties;

    public AiAgentController(
            SimpleAiAgent aiAgent,
            SdWebuiClient sdWebuiClient,
            Dl4jInferenceModule inferenceModule,
            DocumentTrainingService documentTrainingService,
            EdgeTtsService edgeTtsService,
            WcdkProperties properties
    ) {
        this.aiAgent = aiAgent;
        this.sdWebuiClient = sdWebuiClient;
        this.inferenceModule = inferenceModule;
        this.documentTrainingService = documentTrainingService;
        this.edgeTtsService = edgeTtsService;
        this.properties = properties;
    }

    @GetMapping("/health")
    @Operation(summary = "健康状态检查", description = "返回Agent的当前状态")
    public AgentHealthResponse health() {
        return aiAgent.health();
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseStatus(HttpStatus.OK)
    @Operation(summary = "发送chat信息", description = "使用SSE流式返回结果")
    public ResponseEntity<SseEmitter> chat(@Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .cacheControl(CacheControl.noStore())
                .header("X-Accel-Buffering", "no")
                .header("Cache-Control", "no-cache, no-transform")
                .body(aiAgent.chatStream(request));
    }

    public ChatResponse chatText(ChatRequest request) {
        return aiAgent.chatText(request);
    }

    @PostMapping("/txt2img")
    @Operation(summary = "文生图", description = "文字转图片")
    public java.util.List<GeneratedImage> txt2img(@Valid @RequestBody Txt2ImgRequest request) {
        var loraPrompt = request.loraPrompt() == null ? "" : request.loraPrompt().trim();
        var prompt = request.positivePrompt().trim();
        if (!loraPrompt.isBlank() && !prompt.contains(loraPrompt)) {
            prompt = prompt + ", " + loraPrompt;
        }
        return sdWebuiClient.txt2img(prompt, request.negativePrompt());
    }

    @PostMapping(value = "/tts", produces = "audio/mpeg")
    @Operation(summary = "文字转语音", description = "文字转语音")
    public ResponseEntity<byte[]> synthesizeSpeech(@Valid @RequestBody TtsRequest request) {
        return ResponseEntity.ok()
                .contentType(MediaType.valueOf("audio/mpeg"))
                .cacheControl(CacheControl.noStore())
                .body(edgeTtsService.synthesize(request));
    }

    @PostMapping("/train")
    @Operation(summary = "模型意图训练", description = "模型意图训练")
    public TrainingResponse train(@Valid @RequestBody TrainingRequest request) {
        var outputDirectory = request.outputDirectory() == null || request.outputDirectory().isBlank()
                ? Path.of(properties.getRules().getModel())
                : Path.of(request.outputDirectory());
        var epochs = request.epochs() == null ? 250 : request.epochs();
        return inferenceModule.train(request.samples(), epochs, outputDirectory);
    }

    @PostMapping(value = "/documents/train", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "模型文档训练", description = "模型文档训练")
    public DocumentTrainingResponse uploadDocumentAndTrain(@RequestPart("file") MultipartFile file) {
        return documentTrainingService.uploadAndTrain(file);
    }
}
