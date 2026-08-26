package dev.jta.javalin;

import dev.jta.core.AComponent;
import dev.jta.core.AllowAnonymous;
import dev.jta.core.Sse;

/**
 * Fixture minimo de {@code @Sse} - prova de ponta a ponta que
 * {@code JtaJavalin} bridgeia o suporte SSE nativo do Javalin
 * ({@code app.sse(...)}) para {@code SseHub} (jta-runtime).
 */
@Sse(value = "/sse/placar", intervalMillis = 50)
@AllowAnonymous
@AComponent(
    template = "<span id=\"placar\">{{ pontos }}</span>"
)
public class Placar {
    public int pontos = 7;
}
