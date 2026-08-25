package dev.jta.test.fixture;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Fixture minimo - mesmo espirito do {@code Contador} usado nos testes de
 * jta-javalin-starter/jta-standalone: prova de ponta a ponta que
 * JtaTestHarness exercita o pipeline real (processor -> .jte gerado ->
 * jte-maven-plugin -> ComponentRegistry -> dispatchers) sem nenhum
 * container de DI por baixo.
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
