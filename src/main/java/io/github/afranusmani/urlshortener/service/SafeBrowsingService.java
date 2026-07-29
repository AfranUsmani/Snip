package io.github.afranusmani.urlshortener.service;

import io.github.afranusmani.urlshortener.exception.UnsafeUrlException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Screens destination URLs against the Google Safe Browsing Lookup API (v4) so a
 * public shortener isn't trivially used to spread phishing/malware links.
 *
 * <p>Activated only when {@code app.safe-browsing.api-key} is set (via the
 * {@code SAFE_BROWSING_API_KEY} env var); otherwise screening is skipped, so the
 * app runs with zero external dependencies by default. Screening <em>fails
 * open</em>: if the API errors or times out, the link is allowed rather than
 * blocking legitimate use. Safe Browsing is free for non-commercial use.
 */
@Component
public class SafeBrowsingService {

    private static final Logger log = LoggerFactory.getLogger(SafeBrowsingService.class);
    private static final String ENDPOINT =
            "https://safebrowsing.googleapis.com/v4/threatMatches:find";

    private final String apiKey;
    private final RestClient restClient;

    public SafeBrowsingService(@Value("${app.safe-browsing.api-key:}") String apiKey,
                               RestClient.Builder restClientBuilder) {
        this.apiKey = apiKey;
        this.restClient = restClientBuilder.build();
    }

    public boolean isEnabled() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Throws {@link UnsafeUrlException} if the URL is a known threat. No-op when
     * screening is disabled (no API key configured).
     */
    public void check(String url) {
        if (!isEnabled()) {
            return;
        }
        try {
            Map<String, Object> body = Map.of(
                    "client", Map.of("clientId", "snip", "clientVersion", "1.0.0"),
                    "threatInfo", Map.of(
                            "threatTypes", List.of(
                                    "MALWARE", "SOCIAL_ENGINEERING",
                                    "UNWANTED_SOFTWARE", "POTENTIALLY_HARMFUL_APPLICATION"),
                            "platformTypes", List.of("ANY_PLATFORM"),
                            "threatEntryTypes", List.of("URL"),
                            "threatEntries", List.of(Map.of("url", url))
                    )
            );
            Map<?, ?> response = restClient.post()
                    .uri(ENDPOINT + "?key={key}", apiKey)
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (response != null && response.containsKey("matches")) {
                throw new UnsafeUrlException("url was flagged as unsafe (phishing or malware)");
            }
        } catch (UnsafeUrlException e) {
            throw e;
        } catch (Exception e) {
            // Fail open: never block a legitimate link because screening hiccuped.
            log.warn("Safe Browsing check failed, allowing link: {}", e.getMessage());
        }
    }
}
