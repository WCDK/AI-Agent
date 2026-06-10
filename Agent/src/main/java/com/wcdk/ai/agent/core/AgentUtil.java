package com.wcdk.ai.agent.core;

import java.nio.file.Path;
import java.util.List;

import com.wcdk.ai.agent.document.DocumentChunk;
import com.wcdk.ai.agent.document.DocumentTrainingResponse;
import com.wcdk.ai.agent.document.KnowledgeBaseService;
import org.springframework.stereotype.Component;

@Component
/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public class AgentUtil {

    private final KnowledgeBaseService knowledgeBaseService;

    public AgentUtil(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 检索增强生成的建库入口：文档上传并切片完成后，将每个切片写入本地资料库索引。
     * 后续用户提问时会从该索引中召回相关片段，再交给模型生成回答。
     */
    public DocumentTrainingResponse.IndexInfo createIndex(String originalFileName, String storedFilePath, List<DocumentChunk> chunks) {
        var indexPaths = knowledgeBaseService.indexDocument(originalFileName, storedFilePath, chunks);
        return new DocumentTrainingResponse.IndexInfo(
                "INDEXED",
                storedFilePath == null ? "" : storedFilePath.toString(),
                indexPaths.stream()
                        .map(Path::toString)
                        .toList(),
                chunks == null ? 0 : chunks.size()
        );
    }
}
