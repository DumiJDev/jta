package dev.jta.javalin;

import dev.jta.core.AComponent;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;

/**
 * Fixture minimo de {@code @RequiresRole} - usado pelo TCK
 * ({@code JavalinJtaTckTest}) para provar que uma pagina restrita devolve
 * 403 sem autenticacao (o default de {@link JtaJavalinConfig}: usuario
 * sempre anonimo).
 */
@Route("/admin")
@RequiresRole("ADMIN")
@AComponent(template = "<p>Area restrita</p>")
public class AdminPage {
}
