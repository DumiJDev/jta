package dev.jta.quarkus.tck;

import dev.jta.core.AComponent;
import dev.jta.core.AllowAnonymous;
import dev.jta.core.Sse;

/**
 * Fixture minimo de {@code @Sse} - usado pelo TCK ({@code QuarkusJtaTckTest})
 * para provar que {@code JtaSseRouteHandler} (Vert.x cru, chunked, sobre
 * {@code SseHub} de jta-runtime) entrega o HTML re-renderizado a um
 * cliente conectado.
 */
@Sse(value = "/sse/placar", intervalMillis = 50)
@AllowAnonymous
@AComponent(
    template = "<span id=\"placar\">{{ pontos }}</span>"
)
public class Placar {
    public int pontos = 7;
}
