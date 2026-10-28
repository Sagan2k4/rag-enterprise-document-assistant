package com.enterprise.rag.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.document.DocumentRepository;
import com.enterprise.rag.document.DocumentService;
import com.enterprise.rag.document.SourceType;
import com.enterprise.rag.security.Role;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Loads a small fictional company knowledge base (handbook PDF, policies, wiki pages) with different
 * access levels on first start, so search, citations and RBAC can be demoed straight away.
 * Disable with {@code SEED_SAMPLE_DOCS=false}.
 */
@Component
@Order(2)
public class SampleDocumentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDocumentSeeder.class);
    private static final String BASE = "seed-docs/";

    private final DocumentRepository documents;
    private final DocumentService documentService;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    public SampleDocumentSeeder(DocumentRepository documents, DocumentService documentService,
            ObjectMapper objectMapper, RagProperties properties) {
        this.documents = documents;
        this.documentService = documentService;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    record SeedEntry(String file, String title, Set<Role> allowedRoles) {
    }

    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (!properties.seed().sampleDocs() || documents.count() > 0) {
            return;
        }
        List<SeedEntry> entries;
        try (InputStream in = new ClassPathResource(BASE + "manifest.json").getInputStream()) {
            entries = objectMapper.readValue(in, new TypeReference<>() {
            });
        }
        for (SeedEntry entry : entries) {
            try (InputStream in = new ClassPathResource(BASE + entry.file()).getInputStream()) {
                documentService.register(in.readAllBytes(), entry.file(), entry.title(),
                        SourceType.fromFilename(entry.file()), null, entry.allowedRoles(), "system");
            }
        }
        log.info("Queued {} sample documents for ingestion", entries.size());
    }
}
