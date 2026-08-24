package dev.jta.core;

import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Lookup de traducao minimo, usando {@link ResourceBundle} (JDK puro,
 * sem dependencia externa) sobre um bundle chamado {@code messages}
 * (ex: {@code messages.properties}, {@code messages_pt.properties} em
 * {@code src/main/resources}).
 *
 * <p><b>Limitacao conhecida do MVP:</b> usa {@link Locale#getDefault()}
 * (locale da JVM/SO) em vez de negociar o locale por requisicao (ex: via
 * header {@code Accept-Language}) - isso fica para uma fase futura, ja
 * que exigiria um hook do framework hospedeiro para expor o locale da
 * requisicao atual ao componente sendo renderizado. Chaves faltando no
 * bundle em runtime (apesar de validadas em compile-time contra
 * {@code messages.properties} - ver {@code JtaAnnotationProcessor}) caem
 * num fallback visivel ({@code "???chave???"}) em vez de lancar excecao
 * ou silenciar o problema, seguindo a convencao classica de i18n.
 */
public final class Translations {

    private static final String BUNDLE_NAME = "messages";

    private Translations() {
    }

    public static String translate(String key) {
        return translate(key, Locale.getDefault());
    }

    public static String translate(String key, Locale locale) {
        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "???" + key + "???";
        }
    }
}
