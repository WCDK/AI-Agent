package com.wcdk.ai.aiagenttest.agent.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

class Dl4jInferenceModuleTests {

    private final PerceptionModule perceptionModule = new PerceptionModule();
    private Dl4jInferenceModule inferenceModule;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        var properties = new WcdkProperties();
        properties.getRules().setModel(tempDir.toString());
        inferenceModule = new Dl4jInferenceModule(perceptionModule, new ObjectMapper(), properties);
    }

    @Test
    void trainsAndPredictsMergedSamples() {
        inferenceModule.train(List.of(
                new TrainingSample("hello, just chat", "CHAT"),
                new TrainingSample("what is a rule engine?", "ANSWER_QUESTION"),
                new TrainingSample("implement a user login api", "EXECUTE_TASK"),
                new TrainingSample("fix the failing unit test", "EXECUTE_TASK")
        ), 250, tempDir);

        assertIntent("hello, just chat", "CHAT");
        assertIntent("what is a rule engine?", "ANSWER_QUESTION");
        assertIntent("implement a user login api", "EXECUTE_TASK");
        assertIntent("fix the failing unit test", "EXECUTE_TASK");
    }

    @Test
    void exportsTrainingResult() {
        var outputDirectory = tempDir;
        inferenceModule.train(List.of(
                new TrainingSample("hello, just chat", "CHAT"),
                new TrainingSample("what is a rule engine?", "ANSWER_QUESTION")
        ), 5, outputDirectory);

        var modelPath = inferenceModule.exportTrainingResult(outputDirectory);

        System.out.printf("exportedModel=%s%n", modelPath);
        System.out.printf("exportedLabels=%s%n", outputDirectory.resolve("ai-agent-intent-labels.txt"));
        System.out.printf("exportedMetadata=%s%n", outputDirectory.resolve("ai-agent-intent-metadata.txt"));
        System.out.printf("exportedSamples=%s%n", outputDirectory.resolve(Dl4jInferenceModule.SAMPLES_FILE_NAME));

        assertThat(Files.exists(modelPath)).isTrue();
        assertThat(Files.exists(outputDirectory.resolve("history"))).isTrue();
        assertThat(Files.exists(outputDirectory.resolve("ai-agent-intent-labels.txt"))).isTrue();
        assertThat(Files.exists(outputDirectory.resolve("ai-agent-intent-metadata.txt"))).isTrue();
        assertThat(Files.exists(outputDirectory.resolve(Dl4jInferenceModule.SAMPLES_FILE_NAME))).isTrue();
    }

    private void assertIntent(String message, String expectedIntent) {
        var perception = perceptionModule.perceive(message);
        var inference = inferenceModule.infer(perception);

        System.out.printf(
                "message=%s, intent=%s, confidence=%.4f, urgency=%.2f%n",
                message,
                inference.intent(),
                inference.confidence(),
                inference.urgency()
        );

        assertThat(inference.intent()).isEqualTo(expectedIntent);
        assertThat(inference.confidence()).isGreaterThan(0.60);
    }
}
