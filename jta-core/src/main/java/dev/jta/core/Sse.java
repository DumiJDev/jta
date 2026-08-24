package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Expoe um endpoint Server-Sent Events que re-renderiza o componente
 * periodicamente e envia o HTML resultante como evento SSE.
 *
 * <p><b>Limitacao honesta do MVP:</b> este e um mecanismo de
 * <em>polling por intervalo</em>, nao push orientado a evento - o
 * componente e re-renderizado a cada {@link #intervalMillis()}
 * milissegundos, independente de o estado ter mudado ou nao. SSE
 * orientado a evento de verdade (push quando algo realmente muda, nao
 * numa cadencia fixa) exigiria o dev conectar sua propria fonte de
 * eventos (fila, listener) - fica para uma fase futura. Para muitos
 * casos de uso (um placar, uma lista que muda pouco) polling por
 * intervalo e simples e honesto o suficiente para nao precisar de mais.
 *
 * <pre>{@code
 * @Sse(value = "/notificacoes", intervalMillis = 3000)
 * @AComponent(template = "...")
 * public class Notificacoes { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Sse {

    /** Caminho do endpoint SSE, ex: {@code "/notificacoes"}. */
    String value();

    /** Intervalo entre re-renders, em milissegundos. Padrao: 3 segundos. */
    long intervalMillis() default 3000;
}
