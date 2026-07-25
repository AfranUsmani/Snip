package io.github.afranusmani.urlshortener.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for creating a short link.
 *
 * @param url         the original URL to shorten; must be a well-formed http(s) URL
 * @param customAlias optional vanity code (3–32 chars of letters, digits, {@code -} or {@code _});
 *                    when omitted, a random unguessable short code is generated
 * @param expiresAt   optional expiry instant; when set it must be in the future, and the link
 *                    returns HTTP 410 once it passes
 */
public record CreateUrlRequest(
        @NotBlank(message = "url must not be blank")
        @Size(max = 2048, message = "url must not exceed 2048 characters")
        @Pattern(
                regexp = "^https?://.+",
                message = "url must start with http:// or https://"
        )
        String url,

        @Pattern(
                regexp = "^[A-Za-z0-9_-]{3,32}$",
                message = "customAlias must be 3-32 characters of letters, digits, '-' or '_'"
        )
        String customAlias,

        @Future(message = "expiresAt must be in the future")
        Instant expiresAt
) {

    /** Convenience for the common case of shortening a URL with no options. */
    public CreateUrlRequest(String url) {
        this(url, null, null);
    }
}
