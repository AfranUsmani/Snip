package io.github.afranusmani.urlshortener.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Request payload for editing an existing short link. The short code stays the
 * same; only where it points (and, optionally, its expiry) changes.
 *
 * @param url       the new destination URL (http/https)
 * @param expiresAt new expiry; {@code null} clears any existing expiry
 */
public record UpdateUrlRequest(
        @NotBlank(message = "url must not be blank")
        @Size(max = 2048, message = "url must not exceed 2048 characters")
        @Pattern(regexp = "^https?://.+", message = "url must start with http:// or https://")
        String url,

        @Future(message = "expiresAt must be in the future")
        Instant expiresAt
) {
}
