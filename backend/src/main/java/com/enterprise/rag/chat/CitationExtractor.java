package com.enterprise.rag.chat;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds citation markers such as {@code [2]}, {@code [1][3]} or {@code [1, 4]} in a model answer. */
public final class CitationExtractor {

    private static final Pattern MARKER = Pattern.compile("\\[(\\d+(?:\\s*,\\s*\\d+)*)]");

    private CitationExtractor() {
    }

    /**
     * @param sourceCount number of passages in the prompt; markers outside 1..sourceCount are
     *                    hallucinated references and are dropped
     * @return cited passage numbers in order of first appearance
     */
    public static Set<Integer> extract(String answer, int sourceCount) {
        Set<Integer> cited = new LinkedHashSet<>();
        if (answer == null) {
            return cited;
        }
        Matcher m = MARKER.matcher(answer);
        while (m.find()) {
            for (String part : m.group(1).split(",")) {
                int n = Integer.parseInt(part.strip());
                if (n >= 1 && n <= sourceCount) {
                    cited.add(n);
                }
            }
        }
        return cited;
    }
}
