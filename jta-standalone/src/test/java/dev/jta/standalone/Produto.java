package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Fixture para provar {@link RoutePattern}: {@code /produtos/{id}} precisa
 * casar contra {@code /produtos/42} e popular o path variable {@code id}
 * sem nenhum router de framework por baixo.
 */
@Route("/produtos/{id}")
@AComponent(template = "<main><span id=\"id\">{{ id }}</span></main>")
public class Produto {
    public String id = "";
}
