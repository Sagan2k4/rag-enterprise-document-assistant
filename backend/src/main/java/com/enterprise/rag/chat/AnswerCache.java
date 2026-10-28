package com.enterprise.rag.chat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.enterprise.rag.config.RagProperties;
import com.enterprise.rag.security.AuthUser;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Redis cache for answers.
 * <ul>
 * <li>The key includes the user's role set, so a cached answer is only ever served to users with exactly
 * the same permissions — RBAC holds even on cache hits.</li>
 * <li>The key includes a corpus version that is bumped whenever documents are added or removed, which
 * invalidates every cached answer in O(1) without scanning keys.</li>
 * <li>Redis is an optimisation: if it is down, requests fall through to the LLM.</li>
 * </ul>
 */
@Component
public class AnswerCache {

    private static final Logger log = LoggerFactory.getLogger(AnswerCache.class);
    private static final String VERSION_KEY = "rag:corpus-version";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final RagProperties.Cache config;

    public AnswerCache(StringRedisTemplate redis, ObjectMapper objectMapper, RagProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.config = properties.cache();
    }

    public Optional<AskResponse> get(String question, AuthUser user) {
        if (!config.enabled()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(key(question, user));
            return json == null ? Optional.empty() : Optional.of(objectMapper.readValue(json, AskResponse.class));
        } catch (Exception e) {
            log.warn("Answer cache read failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void put(String question, AuthUser user, AskResponse response) {
        if (!config.enabled()) {
            return;
        }
        try {
            redis.opsForValue().set(key(question, user), objectMapper.writeValueAsString(response), config.ttl());
        } catch (Exception e) {
            log.warn("Answer cache write failed: {}", e.getMessage());
        }
    }

    public void invalidateAll() {
        try {
            redis.opsForValue().increment(VERSION_KEY);
        } catch (Exception e) {
            log.warn("Answer cache invalidation failed: {}", e.getMessage());
        }
    }

    private String key(String question, AuthUser user) {
        String version = Optional.ofNullable(redis.opsForValue().get(VERSION_KEY)).orElse("0");
        String material = normalize(question) + "|" + String.join(",", user.roleNames());
        return "rag:answer:v" + version + ":" + sha256(material);
    }

    static String normalize(String question) {
        return question.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N} ]", " ").replaceAll("\\s+", " ").strip();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
