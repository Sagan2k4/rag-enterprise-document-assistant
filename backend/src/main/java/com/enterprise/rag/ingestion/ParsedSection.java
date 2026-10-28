package com.enterprise.rag.ingestion;

/**
 * A contiguous piece of extracted text plus where it came from.
 *
 * @param pageNumber 1-based page for paginated formats (PDF), otherwise null
 * @param section    nearest heading for structured formats (DOCX, Markdown, HTML), otherwise null
 */
public record ParsedSection(String text, Integer pageNumber, String section) {
}
