package com.enterprise.rag.document;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

    List<KnowledgeDocument> findAllByOrderByCreatedAtDesc();

    boolean existsByContentHash(String contentHash);

    List<KnowledgeDocument> findByStatusIn(List<DocumentStatus> statuses);
}
