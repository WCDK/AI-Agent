package com.wcdk.ai.aiagenttest.agent.document;

import java.util.ArrayList;
import java.util.List;

import com.wcdk.ai.aiagenttest.config.WcdkProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @auther WCDK
 * @date 2026/6/10
 * @version 1.0
 **/
@Component
public class DocumentChunker {

    private final WcdkProperties properties;

    public DocumentChunker(WcdkProperties properties) {
        this.properties = properties;
    }

    public List<DocumentChunk> split(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }

        var normalized = text.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (normalized.isBlank()) {
            return List.of();
        }

        int chunkSize = Math.max(200, properties.getDocument().getChunkSize());
        int overlap = Math.max(0, Math.min(properties.getDocument().getChunkOverlap(), chunkSize / 2));

        var chunks = new ArrayList<DocumentChunk>();
        int start = 0;
        int index = 1;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + chunkSize);
            String piece = normalized.substring(start, end).trim();
            if (!piece.isBlank()) {
                chunks.add(new DocumentChunk(index++, piece));
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(start + 1, end - overlap);
        }
        return chunks;
    }
}
