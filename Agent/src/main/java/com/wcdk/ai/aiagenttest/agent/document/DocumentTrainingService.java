package com.wcdk.ai.aiagenttest.agent.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.wcdk.ai.aiagenttest.agent.core.AgentUtil;
import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Service
public class DocumentTrainingService {

    private static final DateTimeFormatter DIRECTORY_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter ARTIFACT_FORMATTER = DateTimeFormatter.ofPattern("HHmmssSSS");

    private final WcdkProperties properties;
    private final DocumentParser documentParser;
    private final DocumentChunker documentChunker;
    private final AgentUtil agentUtil;

    public DocumentTrainingService(
            WcdkProperties properties,
            DocumentParser documentParser,
            DocumentChunker documentChunker,
            AgentUtil agentUtil
    ) {
        this.properties = properties;
        this.documentParser = documentParser;
        this.documentChunker = documentChunker;
        this.agentUtil = agentUtil;
    }

    public synchronized DocumentTrainingResponse uploadAndTrain(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文档不能为空。");
        }

        var originalFileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "unnamed.txt";
        var now = LocalDateTime.now();
        var sourceRoot = Path.of(properties.getDocument().getSourceDirectory());
        var fileFlg = DIRECTORY_FORMATTER.format(now);
        var batchDirectory = sourceRoot.resolve(fileFlg);
        var artifactPrefix = ARTIFACT_FORMATTER.format(now);

        try {
            Files.createDirectories(batchDirectory);
            var storedFileName = resolveStoredFileName(batchDirectory, artifactPrefix, normalizeFileName(originalFileName));
            var storedFilePath = batchDirectory.resolve(storedFileName);

            try (var inputStream = file.getInputStream()) {
                Files.copy(inputStream, storedFilePath, StandardCopyOption.REPLACE_EXISTING);
            }

            var extractedText = documentParser.parse(storedFilePath, originalFileName);
            if (!StringUtils.hasText(extractedText)) {
                throw new IllegalArgumentException("文档未解析出有效文本内容。");
            }

            var artifactName = removeExtension(storedFileName);
            var extractedTextPath = batchDirectory.resolve(artifactName + "-extracted.txt");
            Files.writeString(extractedTextPath, extractedText, StandardCharsets.UTF_8);

            var chunks = documentChunker.split(extractedText);
            if (chunks.isEmpty()) {
                throw new IllegalArgumentException("文档内容过少，无法生成训练数据。");
            }

            var indexInfo = agentUtil.createIndex(originalFileName, fileFlg, chunks);

            return new DocumentTrainingResponse(
                    "INDEXED",
                    originalFileName,
                    indexInfo
            );
        } catch (IOException exception) {
            throw new IllegalStateException("保存上传文档失败。", exception);
        }
    }

    private String normalizeFileName(String originalFileName) {
        var fileName = Path.of(originalFileName).getFileName().toString();
        return fileName.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String resolveStoredFileName(Path batchDirectory, String artifactPrefix, String normalizedFileName) {
        var fileName = StringUtils.hasText(normalizedFileName) ? normalizedFileName : "unnamed.txt";
        var candidate = batchDirectory.resolve(fileName);
        if (!Files.exists(candidate)) {
            return fileName;
        }
        return artifactPrefix + "-" + fileName;
    }

    private String removeExtension(String fileName) {
        var index = fileName.lastIndexOf('.');
        if (index <= 0) {
            return fileName;
        }
        return fileName.substring(0, index);
    }
}
