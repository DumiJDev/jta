package dev.jta.spring;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Fixture minimo de teste - mesmo espirito do {@code Contador} de
 * jta-javalin-starter/jta-standalone: prova de ponta a ponta que
 * annotation processor -> .jte gerado -> jte-maven-plugin -> Spring MVC ->
 * HTMX funciona. Sem {@code @Component}: {@link SpringComponentFactory}
 * cai para construtor sem argumentos quando a classe nao esta registrada
 * como bean Spring, entao um fixture sem DI nao precisa de anotacao
 * nenhuma do Spring.
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
