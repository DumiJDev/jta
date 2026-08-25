package dev.jta.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Ver {@code src/test/resources/messages.properties}/{@code messages_pt.properties}.
 */
class TranslationsTest {

    @AfterEach
    void resetLocaleContext() {
        LocaleContext.clear();
    }

    @Test
    void translateWithExplicitLocaleUsesMatchingBundle() {
        assertEquals("Ola", Translations.translate("greeting", Locale.forLanguageTag("pt")));
        assertEquals("Hello", Translations.translate("greeting", Locale.forLanguageTag("en")));
    }

    @Test
    void translateWithoutExplicitLocaleUsesLocaleContextCurrent() {
        LocaleContext.set(Locale.forLanguageTag("pt"));
        assertEquals("Ola", Translations.translate("greeting"));
    }

    @Test
    void missingKeyFallsBackToVisibleMarker() {
        assertEquals("???nao.existe???", Translations.translate("nao.existe", Locale.US));
    }
}
