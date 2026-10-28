package com.enterprise.rag.ingestion;

import org.springframework.stereotype.Component;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingType;

/**
 * Counts tokens with the cl100k_base encoding. Gemini uses its own tokenizer, so this is a close
 * approximation — good for chunk sizing and as a cost fallback when the API returns no usage.
 */
@Component
public class TokenCounter {

    private static final Encoding ENCODING =
            Encodings.newLazyEncodingRegistry().getEncoding(EncodingType.CL100K_BASE);

    public int count(String text) {
        return text == null || text.isEmpty() ? 0 : ENCODING.countTokens(text);
    }
}
