package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.AllowAnonymous;
import dev.jta.core.Sse;

/**
 * Fixture minimo de {@code @Sse} - prova de ponta a ponta que
 * {@code JtaHttpServer} mantem a {@code HttpExchange} aberta e escreve
 * eventos de {@code SseHub} (jta-runtime) no formato SSE correto.
 */
@Sse(value = "/sse/placar", intervalMillis = 50)
@AllowAnonymous
@AComponent(
    template = "<span id=\"placar\">{{ pontos }}</span>"
)
public class Placar {
    public int pontos = 7;
}
