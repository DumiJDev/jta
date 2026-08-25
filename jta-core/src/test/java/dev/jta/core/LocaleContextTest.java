package dev.jta.core;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocaleContextTest {

    private final Locale originalDefault = LocaleContext.getDefault();

    @AfterEach
    void resetContext() {
        LocaleContext.clear();
        LocaleContext.setDefault(originalDefault);
    }

    @Test
    void currentFallsBackToDefaultWhenNothingSet() {
        LocaleContext.setDefault(Locale.CANADA_FRENCH);
        assertEquals(Locale.CANADA_FRENCH, LocaleContext.current());
    }

    @Test
    void setOverridesCurrentUntilCleared() {
        LocaleContext.setDefault(Locale.US);
        LocaleContext.set(Locale.GERMANY);
        assertEquals(Locale.GERMANY, LocaleContext.current());

        LocaleContext.clear();
        assertEquals(Locale.US, LocaleContext.current());
    }
}
