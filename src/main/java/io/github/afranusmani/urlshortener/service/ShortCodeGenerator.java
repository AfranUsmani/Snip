package io.github.afranusmani.urlshortener.service;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates random, unguessable short codes.
 *
 * <p>Codes are drawn uniformly from a Base62 alphabet using a {@link SecureRandom}
 * source, so they are non-sequential and cannot be enumerated (unlike codes derived
 * from a database id). At {@link #CODE_LENGTH} characters the keyspace is
 * 62<sup>7</sup> ≈ 3.5×10<sup>12</sup>, which keeps codes compact while making guessing
 * or scraping infeasible. Uniqueness is enforced by the caller against the unique
 * index; see {@code UrlService}.
 */
@Component
public class ShortCodeGenerator {

    private static final char[] ALPHABET =
            "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();

    /** Length of a generated short code. Tune here to trade compactness for keyspace. */
    public static final int CODE_LENGTH = 7;

    private final SecureRandom random = new SecureRandom();

    /** @return a fresh random Base62 code of {@link #CODE_LENGTH} characters. */
    public String generate() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
