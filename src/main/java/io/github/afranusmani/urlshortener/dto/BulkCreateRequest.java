package io.github.afranusmani.urlshortener.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request payload for shortening several URLs in one call. Each URL gets a
 * generated Base62 code; invalid URLs are reported per-item rather than failing
 * the whole batch.
 *
 * @param urls the URLs to shorten (1–50 per request)
 */
public record BulkCreateRequest(
        @NotEmpty(message = "urls must not be empty")
        @Size(max = 50, message = "at most 50 urls per request")
        List<@NotBlank(message = "url must not be blank") String> urls
) {
}
