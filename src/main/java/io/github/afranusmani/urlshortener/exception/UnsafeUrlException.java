package io.github.afranusmani.urlshortener.exception;

/**
 * Thrown when a submitted URL is rejected for safety reasons: a disallowed
 * scheme, a host that resolves to a private/loopback/link-local address, or a
 * destination flagged by the malicious-URL screener.
 */
public class UnsafeUrlException extends RuntimeException {
    public UnsafeUrlException(String message) {
        super(message);
    }
}
