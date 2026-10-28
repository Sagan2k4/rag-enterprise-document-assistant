package com.enterprise.rag.document;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import com.enterprise.rag.chat.AnswerCache;
import com.enterprise.rag.common.ApiException;
import com.enterprise.rag.ingestion.IngestionService;
import com.enterprise.rag.security.AuthUser;
import com.enterprise.rag.security.Role;

@Service
public class DocumentService {

    private static final long MAX_WIKI_BYTES = 5 * 1024 * 1024;

    private final DocumentRepository documents;
    private final IngestionService ingestionService;
    private final AnswerCache answerCache;
    private final RestClient restClient;

    public DocumentService(DocumentRepository documents, IngestionService ingestionService, AnswerCache answerCache) {
        this.documents = documents;
        this.ingestionService = ingestionService;
        this.answerCache = answerCache;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /**
     * Registers the document and hands it to the async ingestion pipeline.
     * Returns immediately with status PENDING; clients poll for READY.
     */
    public KnowledgeDocument register(byte[] content, String filename, String title, SourceType sourceType,
            String sourceUrl, Set<Role> allowedRoles, String uploadedBy) {
        if (content == null || content.length == 0) {
            throw ApiException.badRequest("Document is empty");
        }
        if (allowedRoles == null || allowedRoles.isEmpty()) {
            throw ApiException.badRequest("At least one allowed role is required");
        }
        String hash = sha256(content);
        if (documents.existsByContentHash(hash)) {
            throw ApiException.conflict("This document has already been ingested");
        }
        String effectiveTitle = StringUtils.hasText(title) ? title.trim() : stripExtension(filename);
        KnowledgeDocument doc = documents.save(new KnowledgeDocument(effectiveTitle, filename, sourceType, sourceUrl,
                hash, content.length, allowedRoles, uploadedBy));
        ingestionService.ingestAsync(doc.getId(), content);
        return doc;
    }

    /** Fetches an internal wiki page (Confluence, Notion export, intranet...) and ingests it as HTML. */
    public KnowledgeDocument registerWikiPage(String url, String title, Set<Role> allowedRoles, String uploadedBy) {
        URI uri;
        try {
            uri = URI.create(url.trim());
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Invalid URL");
        }
        if (uri.getScheme() == null || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
            throw ApiException.badRequest("Only http(s) URLs are supported");
        }
        byte[] body;
        try {
            body = restClient.get().uri(uri).retrieve().body(byte[].class);
        } catch (Exception e) {
            throw ApiException.badRequest("Could not fetch page: " + e.getMessage());
        }
        if (body != null && body.length > MAX_WIKI_BYTES) {
            throw ApiException.badRequest("Page is larger than 5 MB");
        }
        String filename = (uri.getHost() + uri.getPath()).replaceAll("[^A-Za-z0-9._-]", "_") + ".html";
        return register(body, filename, title, SourceType.WIKI, uri.toString(), allowedRoles, uploadedBy);
    }

    public List<KnowledgeDocument> listVisible(AuthUser user) {
        return documents.findAllByOrderByCreatedAtDesc().stream()
                .filter(d -> user.canAccess(d.getAllowedRoles()))
                .toList();
    }

    public KnowledgeDocument getVisible(Long id, AuthUser user) {
        // Same 404 for "missing" and "forbidden" so restricted titles cannot be probed.
        return documents.findById(id)
                .filter(d -> user.canAccess(d.getAllowedRoles()))
                .orElseThrow(() -> ApiException.notFound("Document not found"));
    }

    public void delete(Long id) {
        if (!documents.existsById(id)) {
            throw ApiException.notFound("Document not found");
        }
        documents.deleteById(id); // chunks are removed by ON DELETE CASCADE
        answerCache.invalidateAll();
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String stripExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        String base = dot > 0 ? filename.substring(0, dot) : filename;
        return base.replace('_', ' ').replace('-', ' ').trim();
    }
}
