package dev.jta.core;

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AcceptLanguageLocaleResolverTest {

    private final AcceptLanguageLocaleResolver resolver = new AcceptLanguageLocaleResolver(Locale.US);

    @Test
    void resolvesHighestWeightedLanguage() {
        assertEquals(Locale.forLanguageTag("pt-BR"), resolver.resolve("pt-BR,pt;q=0.9,en;q=0.8"));
    }

    @Test
    void fallsBackToDefaultWhenHeaderMissing() {
        assertEquals(Locale.US, resolver.resolve(null));
        assertEquals(Locale.US, resolver.resolve(""));
        assertEquals(Locale.US, resolver.resolve("   "));
    }

    @Test
    void fallsBackToDefaultOnMalformedHeader() {
        assertEquals(Locale.US, resolver.resolve(";;;garbage;;;"));
    }

    @Test
    void fallsBackToDefaultOnWildcard() {
        assertEquals(Locale.US, resolver.resolve("*"));
    }

    @Test
    void singleLanguageTagWithoutWeight() {
        assertEquals(Locale.forLanguageTag("fr"), resolver.resolve("fr"));
    }
}
