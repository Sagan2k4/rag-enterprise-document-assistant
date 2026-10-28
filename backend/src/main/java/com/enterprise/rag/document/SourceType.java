package com.enterprise.rag.document;

import java.util.Locale;

public enum SourceType {
    PDF,
    DOCX,
    MARKDOWN,
    HTML,
    TEXT,
    WIKI,
    /** Anything else Apache Tika can extract text from (pptx, rtf, odt, ...). */
    OTHER;

    public static SourceType fromFilename(String filename) {
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot + 1);
        return switch (ext) {
            case "pdf" -> PDF;
            case "docx" -> DOCX;
            case "md", "markdown" -> MARKDOWN;
            case "html", "htm" -> HTML;
            case "txt" -> TEXT;
            default -> OTHER;
        };
    }
}
