package com.wcdk.ai.agent.pipeline;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcdk.ai.agent.rules.InferenceResult;
import com.wcdk.ai.config.WcdkProperties;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.deeplearning4j.nn.weights.WeightInit;
import org.deeplearning4j.util.ModelSerializer;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.springframework.stereotype.Component;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Component
public class Dl4jInferenceModule {

    public static final String SAMPLES_FILE_NAME = "training-samples.jsonl";

    private static final String MODEL_FILE_NAME = "ai-agent-intent-model.zip";
    private static final String[] INTENTS = {"CHAT", "ANSWER_QUESTION", "EXECUTE_TASK"};

    private final PerceptionModule perceptionModule;
    private final ObjectMapper objectMapper;
    private final WcdkProperties properties;
    private volatile MultiLayerNetwork network;
    private volatile String modelSource;

    public Dl4jInferenceModule(
            PerceptionModule perceptionModule,
            ObjectMapper objectMapper,
            WcdkProperties properties
    ) {
        this.perceptionModule = perceptionModule;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.network = loadOrTrainDefaultModel();
    }

    public InferenceResult infer(PerceptionResult perception) {
        var input = features(perception);
        var output = network.output(input, false);
        var intentIndex = output.argMax(1).getInt(0);
        var confidence = output.getDouble(0, intentIndex);
        var urgency = Math.max(perception.risky() ? 0.9 : 0.0, perception.command() ? 0.65 : 0.25);

        return new InferenceResult(INTENTS[intentIndex], confidence, urgency);
    }

    public synchronized TrainingResponse train(List<TrainingSample> samples, int epochs, Path outputDirectory) {
        if (samples == null || samples.isEmpty()) {
            throw new IllegalArgumentException("训练样本不能为空。");
        }

        var effectiveEpochs = Math.max(1, epochs);
        var mergedSamples = mergeAndPersistSamples(outputDirectory, samples);
        var trainedNetwork = loadTrainBaseNetwork(outputDirectory);
        fit(trainedNetwork, mergedSamples, effectiveEpochs);
        this.network = trainedNetwork;
        this.modelSource = "INCREMENTAL_TRAINED_BY_API";

        var modelPath = exportTrainingResult(outputDirectory);
        var versionedModelPath = exportVersionedModel(outputDirectory, modelPath);
        loadModel(modelPath, "LOADED_AFTER_TRAINING:" + modelPath);

        return new TrainingResponse(
                "训练结束，训练结果已加载",
                samples.size(),
                mergedSamples.size(),
                effectiveEpochs,
                modelPath.toString(),
                versionedModelPath.toString(),
                outputDirectory.resolve("ai-agent-intent-labels.txt").toString(),
                outputDirectory.resolve("ai-agent-intent-metadata.txt").toString(),
                outputDirectory.resolve(SAMPLES_FILE_NAME).toString(),
                modelSource
        );
    }

    public Path exportTrainingResult(Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);

            var modelPath = outputDirectory.resolve(MODEL_FILE_NAME);
            var labelsPath = outputDirectory.resolve("ai-agent-intent-labels.txt");
            var metadataPath = outputDirectory.resolve("ai-agent-intent-metadata.txt");

            ModelSerializer.writeModel(network, modelPath.toFile(), true);
            Files.writeString(labelsPath, String.join(System.lineSeparator(), INTENTS), StandardCharsets.UTF_8);
            Files.writeString(
                    metadataPath,
                    "features=messageLength,tokenCount,question,command,risky,chinese" + System.lineSeparator()
                            + "classes=" + String.join(",", INTENTS) + System.lineSeparator()
                            + "source=" + modelSource + System.lineSeparator(),
                    StandardCharsets.UTF_8
            );

            return modelPath;
        } catch (IOException exception) {
            throw new IllegalStateException("导出 DL4J 训练结果失败: " + outputDirectory, exception);
        }
    }

    public String modelSource() {
        return modelSource;
    }

    private MultiLayerNetwork loadOrTrainDefaultModel() {
        var outputDirectory = defaultOutputDirectory();
        var modelPath = outputDirectory.resolve(MODEL_FILE_NAME);
        if (Files.exists(modelPath)) {
            return loadModel(modelPath, "LOADED:" + modelPath);
        }

        var trained = createNetwork();
        var bootstrapSamples = bootstrapSamples();
        fit(trained, bootstrapSamples, 250);
        this.network = trained;
        this.modelSource = "BOOTSTRAP_TRAINED";
        mergeAndPersistSamples(outputDirectory, bootstrapSamples);
        exportTrainingResult(outputDirectory);
        return trained;
    }

    private MultiLayerNetwork loadTrainBaseNetwork(Path outputDirectory) {
        var modelPath = outputDirectory.resolve(MODEL_FILE_NAME);
        if (Files.exists(modelPath)) {
            return loadModel(modelPath, "TRAINING_BASE:" + modelPath);
        }

        var defaultModelPath = defaultOutputDirectory().resolve(MODEL_FILE_NAME);
        if (Files.exists(defaultModelPath)) {
            return loadModel(defaultModelPath, "TRAINING_BASE:" + defaultModelPath);
        }
        return createNetwork();
    }

    private MultiLayerNetwork loadModel(Path modelPath, String source) {
        try {
            var loaded = ModelSerializer.restoreMultiLayerNetwork(modelPath.toFile());
            this.network = loaded;
            this.modelSource = source;
            return loaded;
        } catch (IOException exception) {
            throw new IllegalStateException("训练模型加载失败: " + modelPath, exception);
        }
    }

    private MultiLayerNetwork createNetwork() {
        MultiLayerConfiguration configuration = new NeuralNetConfiguration.Builder()
                .seed(20260528)
                .weightInit(WeightInit.XAVIER)
                .updater(new Adam(0.03))
                .list()
                .layer(new DenseLayer.Builder()
                        .nIn(6)
                        .nOut(12)
                        .activation(Activation.RELU)
                        .build())
                .layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
                        .nIn(12)
                        .nOut(INTENTS.length)
                        .activation(Activation.SOFTMAX)
                        .build())
                .build();

        var created = new MultiLayerNetwork(configuration);
        created.init();
        return created;
    }

    private void fit(MultiLayerNetwork targetNetwork, List<TrainingSample> samples, int epochs) {
        var featureRows = samples.stream()
                .map(sample -> features(perceptionModule.perceive(sample.message())))
                .toArray(org.nd4j.linalg.api.ndarray.INDArray[]::new);
        var labelRows = samples.stream()
                .map(sample -> oneHot(intentIndex(sample.intent())))
                .toArray(double[][]::new);

        var dataSet = new DataSet(Nd4j.vstack(featureRows), Nd4j.create(labelRows));
        for (int epoch = 0; epoch < epochs; epoch++) {
            targetNetwork.fit(dataSet);
        }
    }

    private List<TrainingSample> mergeAndPersistSamples(Path outputDirectory, List<TrainingSample> newSamples) {
        try {
            Files.createDirectories(outputDirectory);
            var samplesPath = outputDirectory.resolve(SAMPLES_FILE_NAME);
            Map<String, TrainingSample> merged = new LinkedHashMap<>();

            if (Files.exists(samplesPath)) {
                for (String line : Files.readAllLines(samplesPath, StandardCharsets.UTF_8)) {
                    if (line.isBlank()) {
                        continue;
                    }
                    var sample = objectMapper.readValue(line, TrainingSample.class);
                    merged.put(sample.message() + "\u0000" + sample.intent(), sample);
                }
            }

            for (TrainingSample sample : newSamples) {
                merged.put(sample.message() + "\u0000" + sample.intent(), sample);
            }

            var lines = merged.values().stream()
                    .map(sample -> {
                        try {
                            return objectMapper.writeValueAsString(sample);
                        } catch (IOException exception) {
                            throw new IllegalStateException("训练样本序列化失败。", exception);
                        }
                    })
                    .toList();
            Files.write(samplesPath, lines, StandardCharsets.UTF_8);
            return List.copyOf(merged.values());
        } catch (IOException exception) {
            throw new IllegalStateException("合并训练样本失败: " + outputDirectory, exception);
        }
    }

    private Path exportVersionedModel(Path outputDirectory, Path currentModelPath) {
        try {
            var historyDirectory = outputDirectory.resolve("history");
            Files.createDirectories(historyDirectory);
            var timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            var versionedModelPath = historyDirectory.resolve("ai-agent-intent-model-" + timestamp + ".zip");
            Files.copy(currentModelPath, versionedModelPath, StandardCopyOption.REPLACE_EXISTING);
            return versionedModelPath;
        } catch (IOException exception) {
            throw new IllegalStateException("导出版本化 DL4J 模型失败: " + outputDirectory, exception);
        }
    }

    private List<TrainingSample> bootstrapSamples() {
        return List.of(
                new TrainingSample("hello there", "CHAT"),
                new TrainingSample("just chat with me", "CHAT"),
                new TrainingSample("good morning", "CHAT"),
                new TrainingSample("what is AI Agent?", "ANSWER_QUESTION"),
                new TrainingSample("how does spring boot work?", "ANSWER_QUESTION"),
                new TrainingSample("why does api return 500?", "ANSWER_QUESTION"),
                new TrainingSample("implement a login api", "EXECUTE_TASK"),
                new TrainingSample("fix this test failure", "EXECUTE_TASK"),
                new TrainingSample("create vue page", "EXECUTE_TASK"),
                new TrainingSample("delete all data", "EXECUTE_TASK")
        );
    }

    private org.nd4j.linalg.api.ndarray.INDArray features(PerceptionResult perception) {
        return Nd4j.create(new double[][]{{
                Math.min(perception.normalizedMessage().length(), 500) / 500.0,
                Math.min(perception.tokenCount(), 100) / 100.0,
                perception.question() ? 1.0 : 0.0,
                perception.command() ? 1.0 : 0.0,
                perception.risky() ? 1.0 : 0.0,
                perception.chinese() ? 1.0 : 0.0
        }});
    }

    private int intentIndex(String intent) {
        for (int i = 0; i < INTENTS.length; i++) {
            if (INTENTS[i].equals(intent)) {
                return i;
            }
        }
        throw new IllegalArgumentException("不支持的意图: " + intent);
    }

    private double[] oneHot(int index) {
        var label = new double[INTENTS.length];
        label[index] = 1.0;
        return label;
    }

    private Path defaultOutputDirectory() {
        return Path.of(properties.getRules().getModel());
    }
}
