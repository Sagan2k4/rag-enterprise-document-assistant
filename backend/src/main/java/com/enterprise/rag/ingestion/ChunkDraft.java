package com.enterprise.rag.ingestion;

/** A chunk ready to be embedded and stored. */
public record ChunkDraft(int index, String text, int tokenCount, Integer pageNumber, String section) {
}
