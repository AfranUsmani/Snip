package io.github.afranusmani.urlshortener.controller;

import io.github.afranusmani.urlshortener.dto.AnalyticsResponse;
import io.github.afranusmani.urlshortener.dto.BulkCreateRequest;
import io.github.afranusmani.urlshortener.dto.BulkCreateResponse;
import io.github.afranusmani.urlshortener.dto.CreateUrlRequest;
import io.github.afranusmani.urlshortener.dto.UpdateUrlRequest;
import io.github.afranusmani.urlshortener.dto.UrlResponse;
import io.github.afranusmani.urlshortener.model.UrlMapping;
import io.github.afranusmani.urlshortener.service.CreateOutcome;
import io.github.afranusmani.urlshortener.service.QrCodeService;
import io.github.afranusmani.urlshortener.service.UrlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/urls")
@Tag(name = "URLs", description = "Create and inspect short links")
public class UrlController {

    private final UrlService service;
    private final QrCodeService qrCodeService;
    private final String configuredBaseUrl;

    public UrlController(UrlService service, QrCodeService qrCodeService,
                         @Value("${app.base-url:}") String configuredBaseUrl) {
        this.service = service;
        this.qrCodeService = qrCodeService;
        this.configuredBaseUrl = configuredBaseUrl;
    }

    /**
     * The public base URL for building short links. Uses {@code app.base-url}
     * when explicitly set (e.g. a branded domain), otherwise derives it from the
     * current request — so links are correct on localhost, Render, or any host
     * without configuration. Honors X-Forwarded-* via forward-headers-strategy.
     */
    private String baseUrl() {
        if (configuredBaseUrl != null && !configuredBaseUrl.isBlank()) {
            return configuredBaseUrl;
        }
        return ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString();
    }

    @PostMapping
    @Operation(summary = "Create a short link for a given URL (optional custom alias and expiry)")
    public ResponseEntity<UrlResponse> create(@Valid @RequestBody CreateUrlRequest request) {
        UrlMapping mapping = service.create(request.url(), request.customAlias(), request.expiresAt());
        String baseUrl = baseUrl();
        UrlResponse body = UrlResponse.from(mapping, baseUrl);
        URI location = UriComponentsBuilder.fromUriString(baseUrl)
                .path("/api/v1/urls/{code}")
                .buildAndExpand(mapping.getShortCode())
                .toUri();
        return ResponseEntity.created(location).body(body);
    }

    @PostMapping("/bulk")
    @Operation(summary = "Shorten several URLs in one request (per-item success/error)")
    public ResponseEntity<BulkCreateResponse> bulk(@Valid @RequestBody BulkCreateRequest request) {
        String baseUrl = baseUrl();
        List<CreateOutcome> outcomes = service.createBulk(request.urls());
        List<BulkCreateResponse.Item> items = outcomes.stream()
                .map(o -> o.error() == null
                        ? BulkCreateResponse.Item.ok(o.requestedUrl(), o.mapping().getShortCode(),
                                UrlResponse.from(o.mapping(), baseUrl).shortUrl())
                        : BulkCreateResponse.Item.failed(o.requestedUrl(), o.error()))
                .toList();
        int created = (int) items.stream().filter(BulkCreateResponse.Item::success).count();
        return ResponseEntity.ok(new BulkCreateResponse(created, items.size() - created, items));
    }

    @GetMapping("/{shortCode}")
    @Operation(summary = "Fetch metadata and hit statistics for a short link")
    public ResponseEntity<UrlResponse> stats(@PathVariable String shortCode) {
        UrlMapping mapping = service.getMapping(shortCode);
        return ResponseEntity.ok(UrlResponse.from(mapping, baseUrl()));
    }

    @PutMapping("/{shortCode}")
    @Operation(summary = "Edit a short link's destination (and expiry); the code stays the same")
    public ResponseEntity<UrlResponse> update(@PathVariable String shortCode,
                                              @Valid @RequestBody UpdateUrlRequest request) {
        UrlMapping mapping = service.update(shortCode, request.url(), request.expiresAt());
        return ResponseEntity.ok(UrlResponse.from(mapping, baseUrl()));
    }

    @GetMapping(value = "/{shortCode}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    @Operation(summary = "Render a QR code (PNG) for a short link")
    public ResponseEntity<byte[]> qr(@PathVariable String shortCode,
                                     @RequestParam(defaultValue = "240") int size) {
        // 404s if the code is unknown, then encode the public short URL.
        service.getMapping(shortCode);
        String shortUrl = baseUrl().replaceAll("/+$", "") + "/" + shortCode;
        byte[] png = qrCodeService.pngFor(shortUrl, size);
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(1, TimeUnit.HOURS).cachePublic())
                .body(png);
    }

    @GetMapping("/{shortCode}/analytics")
    @Operation(summary = "Aggregated click analytics (device, browser, referrer, daily) for a short link")
    public ResponseEntity<AnalyticsResponse> analytics(@PathVariable String shortCode) {
        return ResponseEntity.ok(service.getAnalytics(shortCode));
    }
}
