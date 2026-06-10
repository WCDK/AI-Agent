package com.wcdk.ai.aiagenttest.agent.document;

import java.util.List;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
public record DocumentTrainingResponse(
        String trainingStatus,
        String uploadedFileName,
        IndexInfo indexInfo
) {
    public record IndexInfo(
            String indexStatus,
            String storedFilePath,
            List<String> indexFilePaths,
            int chunkCount
    ) {
    }
}
