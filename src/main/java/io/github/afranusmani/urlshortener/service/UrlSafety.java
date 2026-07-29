package io.github.afranusmani.urlshortener.service;

import org.springframework.stereotype.Component;

/**
 * Single entry point for "is this destination URL safe to shorten?" — composes
 * the structural {@link UrlSafetyValidator} (scheme / private-address checks)
 * with the {@link SafeBrowsingService} reputation lookup. Both throw
 * {@link io.github.afranusmani.urlshortener.exception.UnsafeUrlException} on a
 * rejected URL.
 */
@Component
public class UrlSafety {

    private final UrlSafetyValidator validator;
    private final SafeBrowsingService safeBrowsing;

    public UrlSafety(UrlSafetyValidator validator, SafeBrowsingService safeBrowsing) {
        this.validator = validator;
        this.safeBrowsing = safeBrowsing;
    }

    /** Throws {@code UnsafeUrlException} if the URL must not be shortened. */
    public void check(String url) {
        validator.validate(url);
        safeBrowsing.check(url);
    }
}
