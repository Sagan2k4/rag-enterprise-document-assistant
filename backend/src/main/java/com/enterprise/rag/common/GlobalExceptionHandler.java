package com.enterprise.rag.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ProblemDetail handleApi(ApiException e) {
        return ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail handleIllegalArgument(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception e) {
        if (isModelRateLimited(e)) {
            log.warn("LLM provider rate limit / quota reached: {}", rootMessage(e));
            return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                    "The AI service's usage limit has been reached. Please try again later.");
        }
        log.error("Unhandled error", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Something went wrong. Check the server logs for details.");
    }

    /** Provider SDKs wrap HTTP 429 differently, so match on the cause chain's messages. */
    static boolean isModelRateLimited(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            String msg = t.getMessage();
            if (msg != null && msg.contains("429")
                    && (msg.contains("quota") || msg.contains("rate") || msg.contains("RESOURCE_EXHAUSTED"))) {
                return true;
            }
            if (t.getCause() == t) break;
        }
        return false;
    }

    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) t = t.getCause();
        String msg = String.valueOf(t.getMessage());
        return msg.length() > 200 ? msg.substring(0, 200) + "…" : msg;
    }
}
