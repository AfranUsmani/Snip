package io.github.afranusmani.urlshortener.service;

import io.github.afranusmani.urlshortener.exception.AliasAlreadyExistsException;
import io.github.afranusmani.urlshortener.exception.ShortCodeNotFoundException;
import io.github.afranusmani.urlshortener.model.UrlMapping;
import io.github.afranusmani.urlshortener.repository.UrlRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UrlServiceTest {

    @Mock
    private UrlRepository repository;

    @Mock
    private ClickAnalyticsService clickAnalytics;

    private UrlService service;

    @BeforeEach
    void setUp() {
        service = new UrlService(repository, clickAnalytics, new ShortCodeGenerator());
    }

    @Test
    void createPersistsAndReturnsMapping() {
        UrlMapping stored = new UrlMapping("https://example.com");
        stored.setShortCode("1C");
        when(repository.save(any(UrlMapping.class))).thenReturn(stored);

        UrlMapping result = service.create("https://example.com", null, null);

        assertThat(result.getShortCode()).isEqualTo("1C");
        assertThat(result.getOriginalUrl()).isEqualTo("https://example.com");
    }

    @Test
    void createGeneratesRandomShortCodeWhenNoAlias() {
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        UrlMapping result = service.create("https://example.com", null, null);

        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(repository).save(captor.capture());
        String code = captor.getValue().getShortCode();
        // Random, fixed-length Base62 — not derived from a sequential id.
        assertThat(code).hasSize(ShortCodeGenerator.CODE_LENGTH).matches("[0-9A-Za-z]+");
        assertThat(result.getShortCode()).isEqualTo(code);
    }

    @Test
    void createRetriesShortCodeOnCollision() {
        // First generated code is taken, the next is free.
        when(repository.existsByShortCode(any())).thenReturn(true, false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        UrlMapping result = service.create("https://example.com", null, null);

        assertThat(result.getShortCode()).hasSize(ShortCodeGenerator.CODE_LENGTH);
        verify(repository, times(2)).existsByShortCode(any());
    }

    @Test
    void createWithCustomAliasSetsShortCode() {
        when(repository.existsByShortCode("promo")).thenReturn(false);
        when(repository.save(any(UrlMapping.class))).thenAnswer(inv -> inv.getArgument(0));

        UrlMapping result = service.create("https://example.com", "promo", null);

        ArgumentCaptor<UrlMapping> captor = ArgumentCaptor.forClass(UrlMapping.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getShortCode()).isEqualTo("promo");
        assertThat(result.getShortCode()).isEqualTo("promo");
    }

    @Test
    void createRejectsTakenAlias() {
        when(repository.existsByShortCode("taken")).thenReturn(true);

        assertThatThrownBy(() -> service.create("https://example.com", "taken", null))
                .isInstanceOf(AliasAlreadyExistsException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void createRejectsReservedAlias() {
        assertThatThrownBy(() -> service.create("https://example.com", "api", null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void resolveReturnsOriginalUrl() {
        UrlMapping mapping = new UrlMapping("https://example.com/page");
        mapping.setShortCode("1C");
        when(repository.findByShortCode("1C")).thenReturn(Optional.of(mapping));

        ResolvedUrl resolved = service.resolve("1C");

        assertThat(resolved.originalUrl()).isEqualTo("https://example.com/page");
        assertThat(resolved.isExpired()).isFalse();
    }

    @Test
    void resolveThrowsWhenShortCodeMissing() {
        when(repository.findByShortCode("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolve("missing"))
                .isInstanceOf(ShortCodeNotFoundException.class);
    }

    @Test
    void recordHitIncrementsAndCapturesClick() {
        service.recordHit("1C", "https://twitter.com/x", "Mozilla/5.0");

        verify(repository).incrementHitCount("1C");
        verify(clickAnalytics).record("1C", "https://twitter.com/x", "Mozilla/5.0");
    }
}
