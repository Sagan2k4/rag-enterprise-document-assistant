package com.enterprise.rag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import com.enterprise.rag.document.SourceType;

class DocumentParserTest {

    private final DocumentParser parser = new DocumentParser();

    @Test
    void pdfKeepsPageNumbers() throws IOException {
        List<ParsedSection> sections = parser.parse(seed("employee-handbook.pdf"), "employee-handbook.pdf",
                SourceType.PDF);

        assertThat(sections).extracting(ParsedSection::pageNumber).containsExactly(1, 2, 3, 4);
        assertThat(sections.get(1).text()).contains("20 days of paid annual leave");
    }

    @Test
    void markdownSplitsOnHeadingsAndIgnoresCodeFences() {
        String md = """
                # Title
                intro
                ## Setup
                run this:
                ```
                # not a heading
                ```
                ## Deploy
                tuesday
                """;

        List<ParsedSection> sections = parser.parse(md.getBytes(StandardCharsets.UTF_8), "a.md", SourceType.MARKDOWN);

        assertThat(sections).extracting(ParsedSection::section).containsExactly("Title", "Setup", "Deploy");
        assertThat(sections.get(1).text()).contains("# not a heading");
    }

    @Test
    void htmlDropsChromeAndKeepsTables() throws IOException {
        List<ParsedSection> sections = parser.parse(seed("travel-expense-policy.html"), "t.html", SourceType.HTML);

        assertThat(sections).extracting(ParsedSection::section).contains("Hotels and Per Diem", "Approval Limits");
        String all = String.join("\n", sections.stream().map(ParsedSection::text).toList());
        assertThat(all).doesNotContain("Finance Wiki").doesNotContain("Last updated by");
        assertThat(all).contains("$1,000 to $5,000 | Department director");
    }

    @Test
    void htmlDoesNotDuplicateNestedBlocks() {
        String html = "<html><body><ul><li><p>only once</p></li></ul></body></html>";

        List<ParsedSection> sections = parser.parse(html.getBytes(StandardCharsets.UTF_8), "x.html", SourceType.HTML);

        String all = String.join("\n", sections.stream().map(ParsedSection::text).toList());
        assertThat(all.split("only once", -1)).hasSize(2);
    }

    @Test
    void sourceTypeIsDetectedFromExtension() {
        assertThat(SourceType.fromFilename("Report.PDF")).isEqualTo(SourceType.PDF);
        assertThat(SourceType.fromFilename("notes.markdown")).isEqualTo(SourceType.MARKDOWN);
        assertThat(SourceType.fromFilename("deck.pptx")).isEqualTo(SourceType.OTHER);
    }

    private static byte[] seed(String name) throws IOException {
        try (InputStream in = new ClassPathResource("seed-docs/" + name).getInputStream()) {
            return in.readAllBytes();
        }
    }
}
