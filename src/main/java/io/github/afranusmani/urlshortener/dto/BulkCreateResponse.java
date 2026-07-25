package io.github.afranusmani.urlshortener.dto;

import java.util.List;

/**
 * Result of a bulk shorten request: overall counts plus a per-URL outcome so the
 * caller can see exactly which inputs succeeded and why any failed.
 */
public record BulkCreateResponse(
        int created,
        int failed,
        List<Item> results
) {

    public record Item(
            String url,
            boolean success,
            String shortCode,
            String shortUrl,
            String error
    ) {
        public static Item ok(String url, String shortCode, String shortUrl) {
            return new Item(url, true, shortCode, shortUrl, null);
        }

        public static Item failed(String url, String error) {
            return new Item(url, false, null, null, error);
        }
    }
}
