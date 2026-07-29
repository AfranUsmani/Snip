package io.github.afranusmani.urlshortener.service;

import io.github.afranusmani.urlshortener.exception.UnsafeUrlException;
import org.springframework.stereotype.Component;

import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Rejects URLs that are unsafe to hand a visitor's browser as a redirect target:
 * non-http(s) schemes ({@code javascript:}, {@code data:}, {@code file:}, ...)
 * and literal IP hosts in private, loopback, link-local or cloud-metadata ranges
 * (e.g. {@code 127.0.0.1}, {@code 10.x}, {@code 169.254.169.254}).
 *
 * <p>Snip never fetches the target itself, so this is not classic SSRF
 * protection — it stops the shortener being used to bounce visitors onto
 * internal-only addresses. Hostnames are intentionally <em>not</em> resolved
 * here: doing a DNS lookup on every create would add latency and its own small
 * abuse surface, so we only classify addresses we can determine without network
 * I/O (IP literals), which covers the realistic cases.
 */
@Component
public class UrlSafetyValidator {

    public void validate(String url) {
        final URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw new UnsafeUrlException("url is not a valid URI");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new UnsafeUrlException("url must use the http or https scheme");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new UnsafeUrlException("url must include a host");
        }

        // Strip IPv6 brackets if the JDK left them on, and normalize IDN/case.
        String bare = host.startsWith("[") && host.endsWith("]")
                ? host.substring(1, host.length() - 1)
                : host;
        String normalized = IDN.toASCII(bare).toLowerCase();

        if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
            throw new UnsafeUrlException("url must not point to a private or local address");
        }

        if (isIpLiteral(normalized)) {
            try {
                InetAddress addr = InetAddress.getByName(normalized); // literal -> no DNS lookup
                if (isBlocked(addr)) {
                    throw new UnsafeUrlException("url must not point to a private or local address");
                }
            } catch (UnknownHostException e) {
                // Malformed literal that slipped past the cheap check — allow it;
                // the browser will simply fail to navigate.
            }
        }
    }

    private boolean isIpLiteral(String host) {
        if (host.indexOf(':') >= 0) {
            return true; // IPv6 literal
        }
        return host.matches("(25[0-5]|2[0-4]\\d|1?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|1?\\d?\\d)){3}");
    }

    private boolean isBlocked(InetAddress addr) {
        return addr.isLoopbackAddress()          // 127.0.0.0/8, ::1
                || addr.isAnyLocalAddress()      // 0.0.0.0, ::
                || addr.isLinkLocalAddress()     // 169.254/16 (incl. cloud metadata), fe80::/10
                || addr.isSiteLocalAddress()     // 10/8, 172.16/12, 192.168/16
                || addr.isMulticastAddress()
                || isUniqueLocalIpv6(addr);      // fc00::/7 (not covered above)
    }

    private boolean isUniqueLocalIpv6(InetAddress addr) {
        byte[] bytes = addr.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}
