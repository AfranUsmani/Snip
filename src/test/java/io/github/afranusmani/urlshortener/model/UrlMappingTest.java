package io.github.afranusmani.urlshortener.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class UrlMappingTest {

    @Test
    void isNotExpiredWhenNoExpirySet() {
        UrlMapping mapping = new UrlMapping("https://example.com");

        assertThat(mapping.isExpired()).isFalse();
    }

    @Test
    void isNotExpiredWhenExpiryIsInTheFuture() {
        UrlMapping mapping = new UrlMapping(
                "https://example.com", Instant.now().plus(1, ChronoUnit.HOURS));

        assertThat(mapping.isExpired()).isFalse();
    }

    @Test
    void isExpiredWhenExpiryIsInThePast() {
        UrlMapping mapping = new UrlMapping(
                "https://example.com", Instant.now().minus(1, ChronoUnit.HOURS));

        assertThat(mapping.isExpired()).isTrue();
    }
}
