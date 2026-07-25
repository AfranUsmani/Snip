package io.github.afranusmani.urlshortener.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Persistent mapping between a generated short code and its original long URL.
 *
 * <p>The short code is a random, unguessable token assigned by the service before
 * persist (see {@code ShortCodeGenerator}); it is deliberately unrelated to the
 * numeric {@code id} so links cannot be enumerated by walking sequential ids. The
 * unique index on {@code short_code} enforces uniqueness.
 */
@Entity
@Table(
        name = "url_mapping",
        indexes = @Index(name = "idx_url_mapping_short_code", columnList = "short_code", unique = true)
)
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "url_mapping_seq")
    @SequenceGenerator(name = "url_mapping_seq", sequenceName = "url_mapping_seq", allocationSize = 1)
    private Long id;

    @Column(name = "short_code", nullable = false, unique = true, length = 64)
    private String shortCode;

    @Column(name = "original_url", nullable = false, length = 2048)
    private String originalUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "hit_count", nullable = false)
    private long hitCount;

    /** Optional link expiry; {@code null} means the link never expires. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    protected UrlMapping() {
        // Required by JPA.
    }

    public UrlMapping(String originalUrl) {
        this(originalUrl, null);
    }

    public UrlMapping(String originalUrl, Instant expiresAt) {
        this.originalUrl = originalUrl;
        this.createdAt = Instant.now();
        this.hitCount = 0L;
        this.expiresAt = expiresAt;
    }

    public void incrementHitCount() {
        this.hitCount++;
    }

    public Long getId() {
        return id;
    }

    public String getShortCode() {
        return shortCode;
    }

    public void setShortCode(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public long getHitCount() {
        return hitCount;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    /** @return {@code true} if an expiry is set and already in the past. */
    public boolean isExpired() {
        return expiresAt != null && expiresAt.isBefore(Instant.now());
    }
}
