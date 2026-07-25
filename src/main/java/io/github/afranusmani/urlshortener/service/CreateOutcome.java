package io.github.afranusmani.urlshortener.service;

import io.github.afranusmani.urlshortener.model.UrlMapping;

/**
 * Per-URL result of a bulk create: either a persisted {@link UrlMapping} or an
 * error message, keyed by the originally requested URL.
 */
public record CreateOutcome(String requestedUrl, UrlMapping mapping, String error) {

    public static CreateOutcome ok(String requestedUrl, UrlMapping mapping) {
        return new CreateOutcome(requestedUrl, mapping, null);
    }

    public static CreateOutcome failed(String requestedUrl, String error) {
        return new CreateOutcome(requestedUrl, null, error);
    }
}
