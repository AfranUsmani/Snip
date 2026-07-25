package io.github.afranusmani.urlshortener.service;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ShortCodeGeneratorTest {

    private final ShortCodeGenerator generator = new ShortCodeGenerator();

    @Test
    void generatesCodeOfConfiguredLength() {
        assertThat(generator.generate()).hasSize(ShortCodeGenerator.CODE_LENGTH);
    }

    @Test
    void generatesOnlyBase62Characters() {
        for (int i = 0; i < 1000; i++) {
            assertThat(generator.generate()).matches("[0-9A-Za-z]{" + ShortCodeGenerator.CODE_LENGTH + "}");
        }
    }

    @Test
    void generatesDistinctCodes() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            codes.add(generator.generate());
        }
        // With a 62^7 keyspace, 10k draws should be collision-free in practice.
        assertThat(codes).hasSize(10_000);
    }
}
