package dev.jta.core;

import java.util.Locale;

/**
 * Holder do {@link Locale} resolvido para a requisicao sendo processada
 * pela thread atual - no mesmo espirito de {@code LocaleContextHolder} do
 * Spring, mas dependency-free (jta-core nao tem nenhuma dependencia
 * externa, ver pom.xml).
 *
 * <p><b>Por que thread-local e nao um parametro explicito:</b> o resto do
 * JTA passa estado por requisicao como parametro explicito (ex:
 * {@code CurrentUser} em {@code JtaActionDispatcher#dispatch}), mas
 * {@link Translations#translate(String)} e chamado direto de dentro do
 * bytecode do template JTE gerado - uma chamada estatica de um argumento
 * so, emitida por {@code TemplateTransformer} sem nenhum jeito de
 * injetar um segundo argumento de locale nesse ponto sem reabrir o
 * contrato de codegen do processor. Um holder thread-local e o mesmo
 * compromisso que praticamente todo framework servlet-based faz para
 * esse problema exato (uma thread atende uma requisicao de cada vez).
 *
 * <p>{@code JtaActionDispatcher}/{@code JtaPageDispatcher} chamam
 * {@link #set} no inicio do processamento de uma requisicao (com o
 * locale resolvido via {@link LocaleResolver}) e {@link #clear} num
 * {@code finally} ao final - essencial em qualquer host que reusa
 * threads de um pool (todo servlet container), senao o locale de uma
 * requisicao vazaria para a proxima que calhar de rodar na mesma thread.
 */
public final class LocaleContext {

    private static final ThreadLocal<Locale> CURRENT = new ThreadLocal<>();

    private static volatile Locale defaultLocale = Locale.getDefault();

    private LocaleContext() {
    }

    /** Locale da requisicao atual, ou {@link #getDefault()} se nenhum foi definido (ex: fora de uma requisicao, ou um adaptador que ainda nao chama {@link #set}). */
    public static Locale current() {
        Locale locale = CURRENT.get();
        return locale != null ? locale : defaultLocale;
    }

    public static void set(Locale locale) {
        CURRENT.set(locale);
    }

    /** Limpa o locale desta thread - chamar sempre num {@code finally} apos {@link #set}, para nao vazar entre requisicoes numa thread reusada. */
    public static void clear() {
        CURRENT.remove();
    }

    /** Default global usado por {@link #current()} quando nenhum locale foi definido para a thread atual. */
    public static void setDefault(Locale locale) {
        defaultLocale = locale;
    }

    public static Locale getDefault() {
        return defaultLocale;
    }
}
