package com.enterprise.rag.document;

import java.time.Instant;
import java.util.List;

public record DocumentResponse(
        Long id,
        String title,
        String filename,
        SourceType sourceType,
        String sourceUrl,
        long sizeBytes,
        List<String> allowedRoles,
        DocumentStatus status,
        Integer pageCount,
        int chunkCount,
        int tokenCount,
        double embeddingCostUsd,
        String errorMessage,
        String uploadedBy,
        Instant createdAt) {

    public static DocumentResponse from(KnowledgeDocument d) {
        return new DocumentResponse(d.getId(), d.getTitle(), d.getFilename(), d.getSourceType(), d.getSourceUrl(),
                d.getSizeBytes(), List.of(d.getAllowedRoles()), d.getStatus(), d.getPageCount(), d.getChunkCount(),
                d.getTokenCount(), d.getEmbeddingCostUsd(), d.getErrorMessage(), d.getUploadedBy(), d.getCreatedAt());
    }
}
