package com.enterprise.rag.ingestion;

import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.enterprise.rag.chat.AnswerCache;
import com.enterprise.rag.document.DocumentRepository;
import com.enterprise.rag.document.KnowledgeDocument;
import com.enterprise.rag.observability.CostCalculator;
import com.enterprise.rag.retrieval.ChunkRepository;

/**
 * Ingestion pipeline: parse → chunk → embed → store.
 * Runs on the {@code ingestionExecutor} pool and records its outcome on the document row.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentRepository documents;
    private final DocumentParser parser;
    private final TextChunker chunker;
    private final EmbeddingService embeddingService;
    private final ChunkRepository chunkRepository;
    private final CostCalculator costCalculator;
    private final AnswerCache answerCache;

    public IngestionService(DocumentRepository documents, DocumentParser parser, TextChunker chunker,
            EmbeddingService embeddingService, ChunkRepository chunkRepository, CostCalculator costCalculator,
            AnswerCache answerCache) {
        this.documents = documents;
        this.parser = parser;
        this.chunker = chunker;
        this.embeddingService = embeddingService;
        this.chunkRepository = chunkRepository;
        this.costCalculator = costCalculator;
        this.answerCache = answerCache;
    }

    @Async("ingestionExecutor")
    public void ingestAsync(Long documentId, byte[] content) {
        ingest(documentId, content);
    }

    public void ingest(Long documentId, byte[] content) {
        KnowledgeDocument doc = documents.findById(documentId).orElse(null);
        if (doc == null) {
            log.warn("Document {} disappeared before ingestion", documentId);
            return;
        }
        doc.markProcessing();
        doc = documents.save(doc);
        long start = System.currentTimeMillis();
        try {
            List<ParsedSection> sections = parser.parse(content, doc.getFilename(), doc.getSourceType());
            List<ChunkDraft> chunks = chunker.chunk(sections);
            if (chunks.isEmpty()) {
                throw new IllegalStateException("No extractable text found (scanned PDFs need OCR first)");
            }

            String title = doc.getTitle();
            List<String> inputs = chunks.stream().map(c -> embeddingInput(title, c)).toList();
            EmbeddingService.Result embeddings = embeddingService.embedAll(inputs);
            chunkRepository.replaceChunks(doc.getId(), chunks, embeddings.vectors());

            int tokens = chunks.stream().mapToInt(ChunkDraft::tokenCount).sum();
            Integer pages = sections.stream().map(ParsedSection::pageNumber).filter(Objects::nonNull)
                    .max(Integer::compare).orElse(null);
            doc.markReady(chunks.size(), tokens, pages, costCalculator.embeddingCost(embeddings.tokens()));
            documents.save(doc);
            answerCache.invalidateAll();
            log.info("Ingested '{}' → {} chunks, {} tokens in {} ms", title, chunks.size(), tokens,
                    System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Ingestion failed for document {}", documentId, e);
            doc.markFailed(e.getMessage());
            documents.save(doc);
        }
    }

    /**
     * Prefixing the document title and section gives each chunk's embedding the context it lost when it
     * was cut out of the document ("contextual chunk headers"); the stored content stays unprefixed.
     */
    static String embeddingInput(String title, ChunkDraft chunk) {
        StringBuilder sb = new StringBuilder("Document: ").append(title).append('\n');
        if (chunk.section() != null) {
            sb.append("Section: ").append(chunk.section()).append('\n');
        }
        return sb.append('\n').append(chunk.text()).toString();
    }
}
