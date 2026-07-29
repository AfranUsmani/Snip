package io.github.afranusmani.urlshortener.service;

import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Remembers the short code produced for a given {@code Idempotency-Key} so a
 * retried create request returns the same link instead of creating a duplicate.
 * A bounded in-memory LRU — appropriate for a single free-tier instance.
 */
@Component
public class IdempotencyStore {

    private static final int MAX_ENTRIES = 1000;

    private final Map<String, String> keyToShortCode = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > MAX_ENTRIES;
                }
            });

    /** Returns the short code previously created for this key, or {@code null}. */
    public String get(String key) {
        return keyToShortCode.get(key);
    }

    public void put(String key, String shortCode) {
        keyToShortCode.put(key, shortCode);
    }
}
