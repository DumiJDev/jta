package dev.jta.core;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Implementacao default de {@link LocaleResolver}: parseia o header HTTP
 * {@code Accept-Language} (RFC 9110 - lista de locales com peso opcional
 * {@code q}, ex: {@code "pt-BR,pt;q=0.9,en;q=0.8"}) via
 * {@link Locale.LanguageRange#parse} (JDK puro, ja ordena por peso
 * decrescente), usando o de maior peso como locale resolvido.
 *
 * <p>Cai em {@link #defaultLocale} se o header estiver ausente, vazio,
 * malformado, ou reduzir a um range que nao produz um {@link Locale}
 * valido (ex: o coringa {@code "*"}).
 */
public final class AcceptLanguageLocaleResolver implements LocaleResolver {

    private final Locale defaultLocale;

    public AcceptLanguageLocaleResolver(Locale defaultLocale) {
        this.defaultLocale = Objects.requireNonNull(defaultLocale, "defaultLocale");
    }

    @Override
    public Locale resolve(String acceptLanguageHeader) {
        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
            return defaultLocale;
        }
        try {
            List<Locale.LanguageRange> ranges = Locale.LanguageRange.parse(acceptLanguageHeader);
            if (ranges.isEmpty()) {
                return defaultLocale;
            }
            Locale resolved = Locale.forLanguageTag(ranges.get(0).getRange());
            return resolved.getLanguage().isEmpty() ? defaultLocale : resolved;
        } catch (RuntimeException e) {
            // header malformado - nao vale a pena falhar a requisicao por isso, so cai no default.
            return defaultLocale;
        }
    }
}
