package com.enterprise.rag.ingestion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.enterprise.rag.config.RagProperties;

/**
 * Structure-aware chunker.
 * <p>
 * Text is broken into paragraphs, oversized paragraphs into sentences and oversized sentences into
 * word windows, so every unit fits the token budget. Units are then packed greedily into chunks of at
 * most {@code maxTokens}, and each new chunk starts with the trailing units of the previous one (up to
 * {@code overlapTokens}) so facts spanning a boundary remain retrievable.
 * <p>
 * Chunks never cross a section/page boundary, which keeps page and heading citations exact.
 */
@Component
public class TextChunker {

    private static final Pattern PARAGRAPH_BREAK = Pattern.compile("\\n\\s*\\n");
    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private final TokenCounter tokenCounter;
    private final int maxTokens;
    private final int overlapTokens;

    @Autowired
    public TextChunker(TokenCounter tokenCounter, RagProperties properties) {
        this(tokenCounter, properties.chunking().maxTokens(), properties.chunking().overlapTokens());
    }

    public TextChunker(TokenCounter tokenCounter, int maxTokens, int overlapTokens) {
        if (maxTokens <= 0 || overlapTokens < 0 || overlapTokens >= maxTokens) {
            throw new IllegalArgumentException("Require 0 <= overlapTokens < maxTokens");
        }
        this.tokenCounter = tokenCounter;
        this.maxTokens = maxTokens;
        this.overlapTokens = overlapTokens;
    }

    public List<ChunkDraft> chunk(List<ParsedSection> sections) {
        List<ChunkDraft> chunks = new ArrayList<>();
        for (ParsedSection section : sections) {
            for (String text : split(normalize(section.text()))) {
                chunks.add(new ChunkDraft(chunks.size(), text, tokenCounter.count(text),
                        section.pageNumber(), section.section()));
            }
        }
        return chunks;
    }

    List<String> split(String text) {
        if (text.isBlank()) {
            return List.of();
        }
        List<Unit> units = toUnits(text);
        List<String> chunks = new ArrayList<>();
        Deque<Unit> current = new ArrayDeque<>();
        int currentTokens = 0;

        for (Unit unit : units) {
            if (currentTokens + unit.tokens() > maxTokens && !current.isEmpty()) {
                chunks.add(join(current));
                current = overlapTail(current);
                currentTokens = current.stream().mapToInt(Unit::tokens).sum();
                while (!current.isEmpty() && currentTokens + unit.tokens() > maxTokens) {
                    currentTokens -= current.removeFirst().tokens();
                }
            }
            current.addLast(unit);
            currentTokens += unit.tokens();
        }
        if (!current.isEmpty()) {
            chunks.add(join(current));
        }
        return chunks;
    }

    private Deque<Unit> overlapTail(Deque<Unit> units) {
        Deque<Unit> tail = new ArrayDeque<>();
        int tokens = 0;
        Iterator<Unit> it = units.descendingIterator();
        while (it.hasNext()) {
            Unit unit = it.next();
            if (tokens + unit.tokens() > overlapTokens) {
                break;
            }
            tail.addFirst(unit);
            tokens += unit.tokens();
        }
        return tail;
    }

    /** Breaks text into pieces that each fit within {@code maxTokens}. */
    private List<Unit> toUnits(String text) {
        List<Unit> units = new ArrayList<>();
        for (String paragraph : PARAGRAPH_BREAK.split(text)) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            int tokens = tokenCounter.count(p);
            if (tokens <= maxTokens) {
                units.add(new Unit(p, tokens));
                continue;
            }
            for (String sentence : SENTENCE_BREAK.split(p)) {
                int sentenceTokens = tokenCounter.count(sentence);
                if (sentenceTokens <= maxTokens) {
                    units.add(new Unit(sentence, sentenceTokens));
                } else {
                    units.addAll(wordWindows(sentence));
                }
            }
        }
        return units;
    }

    private List<Unit> wordWindows(String sentence) {
        List<Unit> windows = new ArrayList<>();
        StringBuilder window = new StringBuilder();
        for (String word : WHITESPACE.split(sentence)) {
            String candidate = window.isEmpty() ? word : window + " " + word;
            if (tokenCounter.count(candidate) > maxTokens && !window.isEmpty()) {
                String done = window.toString();
                windows.add(new Unit(done, tokenCounter.count(done)));
                window.setLength(0);
                window.append(word);
            } else {
                window.setLength(0);
                window.append(candidate);
            }
        }
        if (!window.isEmpty()) {
            String done = window.toString();
            windows.add(new Unit(done, tokenCounter.count(done)));
        }
        return windows;
    }

    private static String join(Deque<Unit> units) {
        return units.stream().map(Unit::text).collect(Collectors.joining("\n"));
    }

    /** Collapses layout noise from PDF/HTML extraction while keeping paragraph breaks. */
    static String normalize(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\r", "")
                .replace(' ', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }

    private record Unit(String text, int tokens) {
    }
}
