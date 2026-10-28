package com.enterprise.rag.retrieval;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Turns a natural-language question into a safe Postgres {@code to_tsquery} expression.
 * <p>
 * Terms are OR-ed rather than AND-ed: questions contain many words that never appear together in a
 * passage, and {@code ts_rank_cd} already rewards chunks matching more of them. Only letters and
 * digits survive, so user input can never inject tsquery operators.
 */
public final class KeywordQueryBuilder {

    private static final int MAX_TERMS = 32;

    private KeywordQueryBuilder() {
    }

    public static Optional<String> toOrQuery(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String query = Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(t -> t.length() >= 2)
                .distinct()
                .limit(MAX_TERMS)
                .collect(Collectors.joining(" | "));
        return query.isEmpty() ? Optional.empty() : Optional.of(query);
    }
}
