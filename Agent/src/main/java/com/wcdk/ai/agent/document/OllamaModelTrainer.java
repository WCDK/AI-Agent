package com.wcdk.ai.agent.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Component
public class OllamaModelTrainer {

    private final ObjectMapper objectMapper;

    public OllamaModelTrainer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public TrainingArtifact trainFromDataset(Path workDirectory, String modelName, String baseModel, Path datasetPath) {
        try {
            Files.createDirectories(workDirectory);

            var modelfilePath = workDirectory.resolve("Modelfile");
            var modelfileContent = buildModelfile(baseModel, datasetPath);
            Files.writeString(modelfilePath, modelfileContent, StandardCharsets.UTF_8);

            var commandOutput = createModelWithCli(workDirectory, modelName);
            if (!commandOutput.success()) {
                throw new IllegalStateException("调用 Ollama 创建定制模型失败：\n" + commandOutput.output());
            }

            return new TrainingArtifact(baseModel, "OLLAMA_CREATE_WITH_MODELFILE", commandOutput.output());
        } catch (IOException exception) {
            throw new IllegalStateException("生成 Ollama 训练文件失败。", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("调用 Ollama 创建模型时被中断。", exception);
        }
    }

    private String buildModelfile(String baseModel, Path datasetPath) throws IOException {
        var builder = new StringBuilder();
        builder.append("FROM ").append(baseModel).append(System.lineSeparator());
        builder.append("SYSTEM \"\"\"").append(System.lineSeparator());
        builder.append("你是一个基于企业资料进行回答的中文助手。回答时优先依据已学习资料，不确定时明确说明。")
                .append(System.lineSeparator());
        builder.append("\"\"\"").append(System.lineSeparator());

        for (String line : Files.readAllLines(datasetPath, StandardCharsets.UTF_8)) {
            if (line.isBlank()) {
                continue;
            }
            var example = objectMapper.readValue(line, DatasetExample.class);
            builder.append("MESSAGE user ").append(flatten(example.prompt())).append(System.lineSeparator());
            builder.append("MESSAGE assistant ").append(flatten(example.response())).append(System.lineSeparator());
        }

        return builder.toString();
    }

    private CommandOutput createModelWithCli(Path workDirectory, String modelName) throws IOException, InterruptedException {
        var process = new ProcessBuilder("ollama", "create", modelName)
                .directory(workDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        var exitCode = process.waitFor();
        return new CommandOutput(exitCode == 0, output);
    }

    private String flatten(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r\n", " ")
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    public record TrainingArtifact(
            String baseModel,
            String trainingMode,
            String commandOutput
    ) {
    }

    private record CommandOutput(
            boolean success,
            String output
    ) {
    }

    private record DatasetExample(
            String prompt,
            String response
    ) {
    }
}
