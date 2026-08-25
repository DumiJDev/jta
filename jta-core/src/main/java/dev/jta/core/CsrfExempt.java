package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt-out explicito da verificacao de CSRF (ver {@code JtaActionDispatcher},
 * jta-runtime) para um componente especifico. Mesmo padrao de
 * {@link AllowAnonymous}: marca a intencao explicitamente em vez de deixar
 * um leitor do codigo adivinhar se a ausencia de protecao foi proposital.
 *
 * <p>Uso esperado e raro - endpoints que genuinamente precisam aceitar POST
 * de fora da mesma origem (ex: um webhook). Para o fluxo normal de acoes
 * HTMX do JTA, a protecao nativa (ver {@code HmacCsrfTokenStore}) e
 * transparente para o dev (nenhum trabalho manual necessario), entao nao
 * ha motivo para desativar por padrao.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface CsrfExempt {
}
