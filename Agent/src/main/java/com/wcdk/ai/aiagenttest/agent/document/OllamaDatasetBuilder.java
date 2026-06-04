package com.wcdk.ai.aiagenttest.agent.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

@Component
public class OllamaDatasetBuilder {

    private final ObjectMapper objectMapper;

    public OllamaDatasetBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public int writeDataset(Path datasetPath, String originalFileName, List<DocumentChunk> chunks) {
        try {
            Files.createDirectories(datasetPath.getParent());
            var lines = new ArrayList<String>();
            for (DocumentChunk chunk : chunks) {
                var examples = examplesForChunk(originalFileName, chunk);
                for (DatasetRecord example : examples) {
                    lines.add(objectMapper.writeValueAsString(example));
                }
            }
            Files.write(datasetPath, lines, StandardCharsets.UTF_8);
            return lines.size();
        } catch (IOException exception) {
            throw new IllegalStateException("写入训练数据集失败：" + datasetPath, exception);
        }
    }

    private List<DatasetRecord> examplesForChunk(String originalFileName, DocumentChunk chunk) {
        var records = new LinkedHashSet<DatasetRecord>();
        var sourceName = originalFileName == null ? "未命名文档" : originalFileName;
        var content = chunk.content();

        records.add(new DatasetRecord(
                "你现在学习一份资料，请记住以下内容。",
                "资料《" + sourceName + "》第 " + chunk.index() + " 段内容如下：\n" + content
        ));
        records.add(new DatasetRecord(
                "请总结这段资料的要点。",
                "资料《" + sourceName + "》第 " + chunk.index() + " 段要点如下：\n" + summarize(content)
        ));
        records.add(new DatasetRecord(
                "请根据学习资料回答问题时遵循原文。",
                "已学习资料《" + sourceName + "》第 " + chunk.index() + " 段，回答相关问题时应优先依据这段内容：\n" + content
        ));

        return List.copyOf(records);
    }

    private String summarize(String content) {
        if (content.length() <= 220) {
            return content;
        }
        return content.substring(0, 220) + "……";
    }

    private record DatasetRecord(
            String prompt,
            String response
    ) {
    }
}
