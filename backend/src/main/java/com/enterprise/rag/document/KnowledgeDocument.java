package com.enterprise.rag.document;

import java.time.Instant;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.enterprise.rag.security.Role;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** A source document in the knowledge base (the chunks live in {@code document_chunks}). */
@Entity
@Table(name = "documents")
public class KnowledgeDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false)
    private SourceType sourceType;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "content_hash", nullable = false, unique = true)
    private String contentHash;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_roles", nullable = false, columnDefinition = "text[]")
    private String[] allowedRoles;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "embedding_cost_usd", nullable = false)
    private double embeddingCostUsd;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "uploaded_by")
    private String uploadedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(String title, String filename, SourceType sourceType, String sourceUrl,
            String contentHash, long sizeBytes, Set<Role> allowedRoles, String uploadedBy) {
        this.title = title;
        this.filename = filename;
        this.sourceType = sourceType;
        this.sourceUrl = sourceUrl;
        this.contentHash = contentHash;
        this.sizeBytes = sizeBytes;
        this.allowedRoles = allowedRoles.stream().map(Role::name).sorted().toArray(String[]::new);
        this.uploadedBy = uploadedBy;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void markProcessing() {
        status = DocumentStatus.PROCESSING;
        errorMessage = null;
    }

    public void markReady(int chunkCount, int tokenCount, Integer pageCount, double embeddingCostUsd) {
        this.status = DocumentStatus.READY;
        this.chunkCount = chunkCount;
        this.tokenCount = tokenCount;
        this.pageCount = pageCount;
        this.embeddingCostUsd = embeddingCostUsd;
    }

    public void markFailed(String error) {
        status = DocumentStatus.FAILED;
        errorMessage = error == null ? "Unknown error" : error.substring(0, Math.min(error.length(), 1000));
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getFilename() {
        return filename;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public String getContentHash() {
        return contentHash;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String[] getAllowedRoles() {
        return allowedRoles;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public double getEmbeddingCostUsd() {
        return embeddingCostUsd;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
