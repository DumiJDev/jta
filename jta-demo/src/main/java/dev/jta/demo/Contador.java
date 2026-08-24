package dev.jta.demo;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Exemplo minimo, identico ao do documento de arquitetura original
 * (secao 3). Prova de ponta a ponta: annotation processor -> .jte
 * gerado -> jte-maven-plugin -> render Spring Boot -> HTMX.
 *
 * <p>Usa {@code styleUrl()} (arquivo externo em
 * {@code src/main/resources/jta-templates/dev/jta/demo/Contador.css}) em
 * vez de {@code style()} inline - dogfooding real da feature.
 */
@Route(value = "/contador", layout = SiteLayout.class)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<input type=\"hidden\" name=\"valor\" value=\"{{ valor }}\"/>"
             + "<input type=\"hidden\" name=\"titulo\" value=\"{{ titulo }}\"/>"
             + "<h1>{{ titulo }}</h1>"
             + "<p><span id=\"valor\">{{ valor }}</span></p>"
             + "<button (click)=\"incrementar()\">+</button>"
             + "<p>{{ mensagem() }}</p></main>",
    styleUrl = "Contador.css"
)
public class Contador {
    public int valor = 0;
    public String titulo = "Cliques";

    public void incrementar() {
        valor++;
    }

    public String mensagem() {
        return valor > 10 ? "Muitos!" : "";
    }
}
