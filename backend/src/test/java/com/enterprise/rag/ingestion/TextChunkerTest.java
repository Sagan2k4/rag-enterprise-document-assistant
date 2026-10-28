package com.enterprise.rag.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class TextChunkerTest {

    private final TokenCounter tokens = new TokenCounter();

    @Test
    void shortTextBecomesSingleChunk() {
        TextChunker chunker = new TextChunker(tokens, 100, 10);
        List<ChunkDraft> chunks = chunker.chunk(List.of(new ParsedSection("Hello world.", 3, "Intro")));

        assertThat(chunks).singleElement().satisfies(c -> {
            assertThat(c.text()).isEqualTo("Hello world.");
            assertThat(c.pageNumber()).isEqualTo(3);
            assertThat(c.section()).isEqualTo("Intro");
            assertThat(c.index()).isZero();
        });
    }

    @Test
    void chunksRespectTokenBudget() {
        TextChunker chunker = new TextChunker(tokens, 50, 10);
        String text = IntStream.range(0, 40)
                .mapToObj(i -> "Paragraph number " + i + " explains a detail of the leave policy.")
                .collect(Collectors.joining("\n\n"));

        List<ChunkDraft> chunks = chunker.chunk(List.of(new ParsedSection(text, null, null)));

        assertThat(chunks).hasSizeGreaterThan(5);
        assertThat(chunks).allSatisfy(c -> assertThat(c.tokenCount()).isLessThanOrEqualTo(50));
        assertThat(chunks).extracting(ChunkDraft::index).containsExactlyElementsOf(
                IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void consecutiveChunksOverlap() {
        TextChunker chunker = new TextChunker(tokens, 40, 15);
        String text = IntStream.range(0, 20)
                .mapToObj(i -> "Sentence " + i + " is here.")
                .collect(Collectors.joining("\n\n"));

        List<String> chunks = chunker.split(text);

        assertThat(chunks).hasSizeGreaterThan(1);
        for (int i = 1; i < chunks.size(); i++) {
            String[] previousLines = chunks.get(i - 1).split("\n");
            String lastOfPrevious = previousLines[previousLines.length - 1];
            assertThat(chunks.get(i).split("\n")).contains(lastOfPrevious);
        }
    }

    @Test
    void oversizedSentenceIsSplitIntoWordWindows() {
        TextChunker chunker = new TextChunker(tokens, 20, 0);
        String longSentence = IntStream.range(0, 200).mapToObj(i -> "word" + i).collect(Collectors.joining(" "));

        List<ChunkDraft> chunks = chunker.chunk(List.of(new ParsedSection(longSentence, null, null)));

        assertThat(chunks).hasSizeGreaterThan(5);
        assertThat(chunks).allSatisfy(c -> assertThat(c.tokenCount()).isLessThanOrEqualTo(20));
        String rejoined = chunks.stream().map(ChunkDraft::text).collect(Collectors.joining(" "));
        assertThat(rejoined).isEqualTo(longSentence);
    }

    @Test
    void chunksNeverSpanSections() {
        TextChunker chunker = new TextChunker(tokens, 200, 20);
        List<ChunkDraft> chunks = chunker.chunk(List.of(
                new ParsedSection("Page one text.", 1, null),
                new ParsedSection("Page two text.", 2, null)));

        assertThat(chunks).extracting(ChunkDraft::pageNumber).containsExactly(1, 2);
    }

    @Test
    void blankSectionsProduceNoChunks() {
        TextChunker chunker = new TextChunker(tokens, 100, 10);
        assertThat(chunker.chunk(List.of(new ParsedSection("   \n\n  ", 1, null)))).isEmpty();
    }

    @Test
    void normalizeCollapsesLayoutWhitespace() {
        assertThat(TextChunker.normalize("a   b\t c \r\n\n\n\n d  ")).isEqualTo("a b c\n\nd");
    }

    @Test
    void rejectsOverlapLargerThanChunk() {
        assertThatThrownBy(() -> new TextChunker(tokens, 10, 10)).isInstanceOf(IllegalArgumentException.class);
    }
}
