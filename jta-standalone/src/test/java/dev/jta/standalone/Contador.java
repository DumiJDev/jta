package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Fixture minimo de teste - mesmo espirito do {@code Contador} de jta-demo:
 * prova de ponta a ponta que annotation processor -> .jte gerado ->
 * jte-maven-plugin -> {@link JtaHttpServer} -> HTMX funciona sem nenhum
 * framework/DI por baixo.
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
