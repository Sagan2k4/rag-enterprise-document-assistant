package com.enterprise.rag.ingestion;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Component;

import com.enterprise.rag.document.SourceType;

/**
 * Extracts text from each supported format while keeping the location metadata
 * (page number or section heading) needed for precise citations.
 */
@Component
public class DocumentParser {

    private static final Pattern MD_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*#*\\s*$");
    private static final Set<String> HTML_HEADINGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");
    private static final String HTML_BLOCKS = "h1, h2, h3, h4, h5, h6, p, li, pre, blockquote, tr, dt, dd";

    public List<ParsedSection> parse(byte[] content, String filename, SourceType type) {
        try {
            return switch (type) {
                case PDF -> parsePdf(content);
                case DOCX -> parseDocx(content);
                case MARKDOWN -> parseMarkdown(new String(content, StandardCharsets.UTF_8));
                case HTML, WIKI -> parseHtml(content);
                case TEXT -> List.of(new ParsedSection(new String(content, StandardCharsets.UTF_8), null, null));
                case OTHER -> parseWithTika(content, filename);
            };
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse " + filename, e);
        }
    }

    /** One section per page so every chunk carries an exact page number. */
    List<ParsedSection> parsePdf(byte[] content) throws IOException {
        List<ParsedSection> sections = new ArrayList<>();
        try (PDDocument pdf = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            for (int page = 1; page <= pdf.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(pdf);
                if (!text.isBlank()) {
                    sections.add(new ParsedSection(text, page, null));
                }
            }
        }
        return sections;
    }

    /** Splits on Word heading styles; tables are flattened row by row. */
    List<ParsedSection> parseDocx(byte[] content) throws IOException {
        List<ParsedSection> sections = new ArrayList<>();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content))) {
            String heading = null;
            StringBuilder body = new StringBuilder();
            for (IBodyElement element : doc.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String text = paragraph.getText();
                    if (text == null || text.isBlank()) {
                        continue;
                    }
                    if (isHeadingStyle(paragraph.getStyle())) {
                        flush(sections, body, heading);
                        heading = text.strip();
                    } else {
                        body.append(text.strip()).append("\n\n");
                    }
                } else if (element instanceof XWPFTable table) {
                    table.getRows().forEach(row -> body.append(row.getTableCells().stream()
                            .map(XWPFTableCell::getText)
                            .map(String::strip)
                            .collect(Collectors.joining(" | "))).append('\n'));
                    body.append('\n');
                }
            }
            flush(sections, body, heading);
        }
        return sections;
    }

    List<ParsedSection> parseMarkdown(String markdown) {
        List<ParsedSection> sections = new ArrayList<>();
        String heading = null;
        StringBuilder body = new StringBuilder();
        boolean inCodeFence = false;
        for (String line : markdown.split("\\R")) {
            if (line.strip().startsWith("```")) {
                inCodeFence = !inCodeFence;
            }
            Matcher m = inCodeFence ? null : MD_HEADING.matcher(line);
            if (m != null && m.matches()) {
                flush(sections, body, heading);
                heading = m.group(2).strip();
            } else {
                body.append(line).append('\n');
            }
        }
        flush(sections, body, heading);
        return sections;
    }

    /** Drops page chrome (nav, footer, scripts) and splits on h1–h6. */
    List<ParsedSection> parseHtml(byte[] content) throws IOException {
        org.jsoup.nodes.Document html = Jsoup.parse(new ByteArrayInputStream(content), null, "");
        html.select("script, style, noscript, nav, header, footer, aside, form, iframe").remove();
        Element body = html.body();
        List<ParsedSection> sections = new ArrayList<>();
        String heading = html.title().isBlank() ? null : html.title().strip();
        StringBuilder text = new StringBuilder();

        for (Element el : body.select(HTML_BLOCKS)) {
            // Skip blocks nested in another selected block (e.g. <p> inside <li>) to avoid duplicates.
            if (el.parents().stream().anyMatch(p -> p.is(HTML_BLOCKS))) {
                continue;
            }
            String tag = el.tagName().toLowerCase(Locale.ROOT);
            if (HTML_HEADINGS.contains(tag)) {
                flush(sections, text, heading);
                heading = el.text().strip();
            } else if (tag.equals("tr")) {
                text.append(el.children().stream().map(Element::text).collect(Collectors.joining(" | "))).append('\n');
            } else {
                String t = tag.equals("pre") ? el.wholeText() : el.text();
                text.append(tag.equals("li") ? "- " : "").append(t.strip()).append("\n\n");
            }
        }
        flush(sections, text, heading);
        if (sections.isEmpty() && !body.text().isBlank()) {
            sections.add(new ParsedSection(body.text(), null, heading));
        }
        return sections;
    }

    /** Fallback for any other format Apache Tika understands. */
    List<ParsedSection> parseWithTika(byte[] content, String filename) {
        ByteArrayResource resource = new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
        String text = new TikaDocumentReader(resource).get().stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));
        return text.isBlank() ? List.of() : List.of(new ParsedSection(text, null, null));
    }

    private static boolean isHeadingStyle(String style) {
        if (style == null) {
            return false;
        }
        String s = style.toLowerCase(Locale.ROOT);
        return s.startsWith("heading") || s.equals("title");
    }

    private static void flush(List<ParsedSection> sections, StringBuilder body, String heading) {
        String text = body.toString().strip();
        if (!text.isEmpty()) {
            sections.add(new ParsedSection(text, null, heading));
        }
        body.setLength(0);
    }
}
