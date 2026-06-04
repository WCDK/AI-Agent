package com.wcdk.ai.aiagenttest.agent.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class KnowledgeBaseService {

    private static final String LEGACY_INDEX_FILE_NAME = "knowledge-base.jsonl";
    private static final String INDEX_FILE_PREFIX = "knowledge-base-";
    private static final String INDEX_FILE_SUFFIX = ".jsonl";
    private static final String KNOWLEDGE_DIRECTORY_NAME = "knowledge";
    private static final long MAX_INDEX_FILE_BYTES = 40L * 1024L * 1024L;
    private static final int DEFAULT_LIMIT = 5;

    private final WcdkProperties properties;
    private final ObjectMapper objectMapper;

    public KnowledgeBaseService(WcdkProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 检索增强生成的索引阶段：把文档切片追加写入本地索引分片。
     * 每个索引分片最大控制在 40MB 左右，超过后自动写入下一个分片。
     */
    public synchronized List<Path> indexDocument(String originalFileName, String storedFilePath, List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }

        var writtenIndexPaths = new LinkedHashSet<Path>();
        try {
            Files.createDirectories(indexDirectory());
            var storedPath = storedFilePath == null ? "" : storedFilePath;
            var sourceName = StringUtils.hasText(originalFileName) ? originalFileName : "unnamed";
            var now = Instant.now().toString();

            for (var chunk : chunks) {
                var line = objectMapper.writeValueAsString(new KnowledgeRecord(
                        sourceName,
                        storedPath,
                        chunk.index(),
                        chunk.content(),
                        now
                ));

                var indexPath = writableIndexPath(line);
                Files.write(indexPath, List.of(line), StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
                writtenIndexPaths.add(indexPath);
            }

            return List.copyOf(writtenIndexPaths);
        } catch (IOException exception) {
            throw new IllegalStateException("写入资料库索引失败：" + indexDirectory(), exception);
        }
    }

    public List<KnowledgeHit> search(String query) {
        return search(query, DEFAULT_LIMIT);
    }

    /**
     * 检索增强生成的召回阶段：将用户问题拆成检索词，在所有索引分片中查找最相关的资料片段。
     * 当前实现是轻量关键词检索，适合本地小型资料库；后续可替换为向量化语义检索。
     */
    public synchronized List<KnowledgeHit> search(String query, int limit) {
        if (!StringUtils.hasText(query)) {
            return List.of();
        }

        var queryTerms = terms(query);
        if (queryTerms.isEmpty()) {
            return List.of();
        }

        try {
            var hits = new ArrayList<KnowledgeHit>();
            for (var indexPath : indexPaths()) {
                for (var line : Files.readAllLines(indexPath, StandardCharsets.UTF_8)) {
                    if (!StringUtils.hasText(line)) {
                        continue;
                    }
                    var record = objectMapper.readValue(line, KnowledgeRecord.class);
                    var score = score(queryTerms, record.content());
                    if (score > 0) {
                        hits.add(new KnowledgeHit(
                                record.sourceFileName(),
                                record.storedFilePath(),
                                record.chunkIndex(),
                                record.content(),
                                score
                        ));
                    }
                }
            }

            return hits.stream()
                    .sorted(Comparator.comparingInt(KnowledgeHit::score).reversed()
                            .thenComparing(KnowledgeHit::sourceFileName)
                            .thenComparingInt(KnowledgeHit::chunkIndex))
                    .limit(Math.max(1, limit))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("检索资料库失败：" + indexDirectory(), exception);
        }
    }

    private int score(Set<String> queryTerms, String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }

        // 命中用户问题中的词越多，片段得分越高；完整子串命中权重大于普通词项命中。
        var contentText = content.toLowerCase(Locale.ROOT);
        var contentTerms = terms(contentText);
        var score = 0;
        for (var term : queryTerms) {
            if (contentText.contains(term)) {
                score += term.length() >= 2 ? 8 : 3;
            }
            if (contentTerms.contains(term)) {
                score += 2;
            }
        }
        return score;
    }

    private Set<String> terms(String text) {
        var normalized = text.toLowerCase(Locale.ROOT);
        var terms = new LinkedHashSet<String>();

        // 先按非字母、数字、汉字字符切词，保留英文单词、数字和连续中文词块。
        for (var token : normalized.split("[^\\p{IsAlphabetic}\\p{IsDigit}\\p{IsHan}]+")) {
            if (token.length() >= 2) {
                terms.add(token);
            }
        }

        // 中文没有天然空格分词，这里补充相邻双字词，提高短中文问题的召回率。
        var chineseChars = new ArrayList<Character>();
        for (var index = 0; index < normalized.length(); index++) {
            var ch = normalized.charAt(index);
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                chineseChars.add(ch);
            }
        }
        for (var index = 0; index + 1 < chineseChars.size(); index++) {
            terms.add("" + chineseChars.get(index) + chineseChars.get(index + 1));
        }

        return terms;
    }

    private Path writableIndexPath(String nextLine) throws IOException {
        var lineBytes = (nextLine + System.lineSeparator()).getBytes(StandardCharsets.UTF_8).length;
        var shardedPaths = shardedIndexPaths();
        if (shardedPaths.isEmpty()) {
            return shardPath(1);
        }

        var currentPath = shardedPaths.getLast();
        var currentSize = Files.exists(currentPath) ? Files.size(currentPath) : 0L;
        if (currentSize > 0 && currentSize + lineBytes > MAX_INDEX_FILE_BYTES) {
            return shardPath(shardNumber(currentPath) + 1);
        }
        return currentPath;
    }

    private List<Path> shardedIndexPaths() throws IOException {
        var allPaths = indexPaths();
        return allPaths.stream()
                .filter(this::isShardedIndexPath)
                .sorted(Comparator.comparingInt(this::shardNumber))
                .toList();
    }

    public List<Path> indexPaths() throws IOException {
        var directory = indexDirectory();
        if (!Files.exists(directory)) {
            return List.of();
        }

        try (var stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> isLegacyIndexPath(path) || isShardedIndexPath(path))
                    .sorted((left, right) -> {
                        if (isLegacyIndexPath(left) && !isLegacyIndexPath(right)) {
                            return -1;
                        }
                        if (!isLegacyIndexPath(left) && isLegacyIndexPath(right)) {
                            return 1;
                        }
                        return Integer.compare(shardNumber(left), shardNumber(right));
                    })
                    .toList();
        }
    }

    private Path indexDirectory() {
        return Path.of(properties.getDocument().getSourceDirectory()).resolve(KNOWLEDGE_DIRECTORY_NAME);
    }

    private Path shardPath(int shardNumber) {
        return indexDirectory().resolve(INDEX_FILE_PREFIX + "%06d".formatted(shardNumber) + INDEX_FILE_SUFFIX);
    }

    private boolean isLegacyIndexPath(Path path) {
        return LEGACY_INDEX_FILE_NAME.equals(path.getFileName().toString());
    }

    private boolean isShardedIndexPath(Path path) {
        var fileName = path.getFileName().toString();
        return fileName.startsWith(INDEX_FILE_PREFIX) && fileName.endsWith(INDEX_FILE_SUFFIX) && shardNumber(path) > 0;
    }

    private int shardNumber(Path path) {
        var fileName = path.getFileName().toString();
        if (!fileName.startsWith(INDEX_FILE_PREFIX) || !fileName.endsWith(INDEX_FILE_SUFFIX)) {
            return 0;
        }

        var numberText = fileName.substring(INDEX_FILE_PREFIX.length(), fileName.length() - INDEX_FILE_SUFFIX.length());
        try {
            return Integer.parseInt(numberText);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    /**
     * 检索增强生成的增强阶段：把召回片段整理成模型可读的上下文，稍后拼入系统提示词。
     */
    public String buildContext(List<KnowledgeHit> hits) {
        if (hits == null || hits.isEmpty()) {
            return "";
        }

        var grouped = new LinkedHashMap<String, List<KnowledgeHit>>();
        for (var hit : hits) {
            grouped.computeIfAbsent(hit.sourceFileName(), ignored -> new ArrayList<>()).add(hit);
        }

        var builder = new StringBuilder();
        builder.append("资料库检索结果：").append(System.lineSeparator());
        for (Map.Entry<String, List<KnowledgeHit>> entry : grouped.entrySet()) {
            builder.append("来源：").append(entry.getKey()).append(System.lineSeparator());
            for (var hit : entry.getValue()) {
                builder.append("- 第 ").append(hit.chunkIndex()).append(" 段：")
                        .append(hit.content().replaceAll("\\s+", " ").trim())
                        .append(System.lineSeparator());
            }
        }
        return builder.toString();
    }

    private record KnowledgeRecord(
            String sourceFileName,
            String storedFilePath,
            int chunkIndex,
            String content,
            String indexedAt
    ) {
    }

    public record KnowledgeHit(
            String sourceFileName,
            String storedFilePath,
            int chunkIndex,
            String content,
            int score
    ) {
    }
}
