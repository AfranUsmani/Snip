package io.github.afranusmani.urlshortener.service;

import io.github.afranusmani.urlshortener.dto.AnalyticsResponse;
import io.github.afranusmani.urlshortener.exception.AliasAlreadyExistsException;
import io.github.afranusmani.urlshortener.exception.ShortCodeNotFoundException;
import io.github.afranusmani.urlshortener.exception.UnsafeUrlException;
import io.github.afranusmani.urlshortener.model.UrlMapping;
import io.github.afranusmani.urlshortener.repository.UrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Core business logic for creating and resolving short links.
 *
 * <p>The read path ({@link #resolve(String)}) is cache-aside: hot short codes
 * are served from the cache and only miss through to the database, which keeps
 * redirect latency low under load.
 */
@Service
public class UrlService {

    private static final Logger log = LoggerFactory.getLogger(UrlService.class);

    /**
     * Aliases that would shadow first-class application routes. Compared
     * case-insensitively so, e.g., {@code API} is rejected too.
     */
    private static final Set<String> RESERVED_ALIASES = Set.of(
            "api", "actuator", "swagger-ui", "h2-console", "v3", "webjars",
            "error", "favicon", "index", "assets", "static", "qr", "analytics",
            "robots", "sitemap", "login", "admin", "preview", "p", "bulk"
    );

    /** Max URLs accepted in a single bulk request (mirrored by bean validation). */
    private static final int BULK_LIMIT = 50;
    private static final String URL_PATTERN = "^https?://.+";

    /**
     * Retries when a randomly generated code collides with an existing one.
     * Collisions are astronomically unlikely at the configured code length, so a
     * handful of attempts is more than enough; exhausting them signals a real
     * problem rather than bad luck.
     */
    private static final int MAX_CODE_ATTEMPTS = 5;

    private final UrlRepository repository;
    private final ClickAnalyticsService clickAnalytics;
    private final ShortCodeGenerator shortCodeGenerator;
    private final UrlSafety urlSafety;

    public UrlService(UrlRepository repository, ClickAnalyticsService clickAnalytics,
                      ShortCodeGenerator shortCodeGenerator, UrlSafety urlSafety) {
        this.repository = repository;
        this.clickAnalytics = clickAnalytics;
        this.shortCodeGenerator = shortCodeGenerator;
        this.urlSafety = urlSafety;
    }

    /**
     * Creates a new short link.
     *
     * <p>Without a custom alias a random, unguessable short code is generated (see
     * {@link ShortCodeGenerator}) and retried on the rare collision, so codes cannot
     * be enumerated by walking sequential ids. With a custom alias, availability is
     * checked up front for a friendly 409, and the unique index backs it up against
     * races.
     */
    @Transactional
    public UrlMapping create(String originalUrl, String customAlias, Instant expiresAt) {
        urlSafety.check(originalUrl);
        UrlMapping mapping = new UrlMapping(originalUrl, expiresAt);

        if (StringUtils.hasText(customAlias)) {
            String alias = customAlias.trim();
            ensureAliasAllowed(alias);
            if (repository.existsByShortCode(alias)) {
                throw new AliasAlreadyExistsException(alias);
            }
            mapping.setShortCode(alias);
        } else {
            mapping.setShortCode(generateUniqueShortCode());
        }

        UrlMapping saved = repository.save(mapping);
        log.info("Created short code '{}' for url '{}'{}",
                saved.getShortCode(), originalUrl,
                expiresAt != null ? " (expires " + expiresAt + ")" : "");
        return saved;
    }

    private void ensureAliasAllowed(String alias) {
        if (RESERVED_ALIASES.contains(alias.toLowerCase())) {
            throw new IllegalArgumentException("customAlias '" + alias + "' is reserved");
        }
    }

    /**
     * Generates a random short code that is not already in use. The unique index
     * is the ultimate guard against a concurrent race; this pre-check just keeps
     * the common case clean and gives collisions a fresh code to retry with.
     */
    private String generateUniqueShortCode() {
        for (int attempt = 0; attempt < MAX_CODE_ATTEMPTS; attempt++) {
            String code = shortCodeGenerator.generate();
            if (!repository.existsByShortCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException(
                "could not generate a unique short code after " + MAX_CODE_ATTEMPTS + " attempts");
    }

    /**
     * Edits an existing link's destination (and expiry). The short code is
     * unchanged, so any printed/QR-encoded links keep working. Evicts the resolve
     * cache for this code so the next redirect reflects the new target.
     */
    @CacheEvict(cacheNames = "urls", key = "#shortCode")
    @Transactional
    public UrlMapping update(String shortCode, String newUrl, Instant expiresAt) {
        urlSafety.check(newUrl);
        UrlMapping mapping = repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
        mapping.setOriginalUrl(newUrl);
        mapping.setExpiresAt(expiresAt);
        UrlMapping saved = repository.save(mapping);
        log.info("Updated short code '{}' -> '{}'", shortCode, newUrl);
        return saved;
    }

    /**
     * Deletes a link so its code stops resolving. Evicts the resolve cache for
     * the code. Click-analytics rows for the code are left as harmless orphans
     * (unreachable once the mapping is gone, and reset with the DB).
     */
    @CacheEvict(cacheNames = "urls", key = "#shortCode")
    @Transactional
    public void delete(String shortCode) {
        if (!repository.existsByShortCode(shortCode)) {
            throw new ShortCodeNotFoundException(shortCode);
        }
        repository.deleteByShortCode(shortCode);
        log.info("Deleted short code '{}'", shortCode);
    }

    /**
     * Shortens several URLs in one call. Each URL is validated and created
     * independently, so one bad input reports an error instead of failing the
     * whole batch.
     */
    @Transactional
    public List<CreateOutcome> createBulk(List<String> urls) {
        if (urls.size() > BULK_LIMIT) {
            throw new IllegalArgumentException("at most " + BULK_LIMIT + " urls per request");
        }
        List<CreateOutcome> outcomes = new ArrayList<>(urls.size());
        for (String url : urls) {
            String trimmed = url == null ? "" : url.trim();
            if (!trimmed.matches(URL_PATTERN) || trimmed.length() > 2048) {
                outcomes.add(CreateOutcome.failed(url, "url must start with http:// or https:// and be ≤ 2048 chars"));
                continue;
            }
            try {
                urlSafety.check(trimmed);
            } catch (UnsafeUrlException e) {
                outcomes.add(CreateOutcome.failed(url, e.getMessage()));
                continue;
            }
            UrlMapping mapping = new UrlMapping(trimmed, null);
            mapping.setShortCode(generateUniqueShortCode());
            UrlMapping saved = repository.save(mapping);
            outcomes.add(CreateOutcome.ok(url, saved));
        }
        return outcomes;
    }

    /**
     * Resolves a short code to its target (plus expiry). Results are cached; the
     * not-found case is not cached because the exception short-circuits
     * {@code @Cacheable}. Expiry is carried in the cached value so it is checked
     * by the caller on every redirect, not just on cache misses.
     */
    @Cacheable(cacheNames = "urls", key = "#shortCode")
    @Transactional(readOnly = true)
    public ResolvedUrl resolve(String shortCode) {
        return repository.findByShortCode(shortCode)
                .map(m -> new ResolvedUrl(m.getOriginalUrl(), m.getExpiresAt()))
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
    }

    /**
     * Records a redirect hit. The atomic counter bump is synchronous (a single
     * cheap UPDATE); the detailed analytics event is captured asynchronously so
     * it never adds latency to the redirect.
     */
    @Transactional
    public void recordHit(String shortCode, String referer, String userAgent) {
        repository.incrementHitCount(shortCode);
        clickAnalytics.record(shortCode, referer, userAgent);
    }

    @Transactional(readOnly = true)
    public UrlMapping getMapping(String shortCode) {
        return repository.findByShortCode(shortCode)
                .orElseThrow(() -> new ShortCodeNotFoundException(shortCode));
    }

    /** Aggregated analytics for a code; 404s if the code does not exist. */
    @Transactional(readOnly = true)
    public AnalyticsResponse getAnalytics(String shortCode) {
        if (!repository.existsByShortCode(shortCode)) {
            throw new ShortCodeNotFoundException(shortCode);
        }
        return clickAnalytics.summarize(shortCode);
    }
}
