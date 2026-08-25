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
 * <p>Usa {@link LocaleContext#current()} (o locale resolvido para a
 * requisicao atual, ver {@code LocaleResolver}/{@code JtaActionDispatcher}/
 * {@code JtaPageDispatcher}) em vez de {@link Locale#getDefault()} - a
 * limitacao de "sempre usa o locale da JVM/SO" documentada aqui numa fase
 * anterior foi corrigida na fase de correcao de dados: o dispatcher
 * resolve o locale por requisicao (ex: via header {@code Accept-Language})
 * e o publica em {@link LocaleContext} antes de renderizar, que e onde
 * esta chamada (emitida direto no bytecode do template gerado por
 * {@code TemplateTransformer}) acaba lendo. Chaves faltando no bundle em
 * runtime (apesar de validadas em compile-time contra
 * {@code messages.properties} - ver {@code JtaAnnotationProcessor}) caem
 * num fallback visivel ({@code "???chave???"}) em vez de lancar excecao
 * ou silenciar o problema, seguindo a convencao classica de i18n.
 */
public final class Translations {

    private static final String BUNDLE_NAME = "messages";

    private Translations() {
    }

    public static String translate(String key) {
        return translate(key, LocaleContext.current());
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
