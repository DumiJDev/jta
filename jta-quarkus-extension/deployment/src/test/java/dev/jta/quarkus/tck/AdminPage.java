package dev.jta.quarkus.tck;

import dev.jta.core.AComponent;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;

/**
 * Fixture minimo de {@code @RequiresRole} - usado pelo TCK
 * ({@code QuarkusJtaTckTest}) para provar que uma pagina restrita devolve
 * 403 sem autenticacao ({@code QuarkusCurrentUser.current()} cai para
 * anonimo quando nao ha {@code SecurityIdentity} autenticado).
 */
@Route("/admin")
@RequiresRole("ADMIN")
@AComponent(template = "<p>Area restrita</p>")
public class AdminPage {
}
