package dev.jta.quarkus.tck;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Fixture minimo de teste - mesmo espirito do {@code Contador} dos outros
 * 3 adaptadores: prova de ponta a ponta que annotation processor -> .jte
 * gerado -> jte-maven-plugin -> Vert.x Web (via {@code JtaPageRouteHandler}/
 * {@code JtaActionRouteHandler}) -> HTMX funciona. {@link QuarkusComponentFactory}
 * (jta-quarkus-extension) cai para construtor sem argumentos quando a
 * classe nao esta registrada como bean CDI, entao um fixture sem DI nao
 * precisa de anotacao nenhuma do CDI.
 */
@Route("/contador")
@AComponent(
    template = "<main>"
             + "<input type=\"hidden\" name=\"valor\" value=\"{{ valor }}\"/>"
             + "<span id=\"valor\">{{ valor }}</span>"
             + "<button (click)=\"incrementar()\">+</button>"
             + "</main>"
)
public class Contador {
    public int valor = 0;

    public void incrementar() {
        valor++;
    }
}
