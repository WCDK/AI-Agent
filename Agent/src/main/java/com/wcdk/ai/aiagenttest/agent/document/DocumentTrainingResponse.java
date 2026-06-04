package com.wcdk.ai.aiagenttest.agent.document;

import java.util.List;

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
