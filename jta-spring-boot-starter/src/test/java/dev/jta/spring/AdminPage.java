package dev.jta.spring;

import dev.jta.core.AComponent;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;

/**
 * Fixture minimo de {@code @RequiresRole} - usado pelo TCK
 * ({@code SpringJtaTckTest}) para provar que uma pagina restrita devolve
 * 403 sem autenticacao. Este modulo nao configura nenhum
 * {@code SecurityFilterChain} (so {@code spring-security-core}, sem
 * {@code spring-boot-starter-security}), entao toda requisicao chega sem
 * {@code Authentication} no {@code SecurityContextHolder} - exatamente o
 * caso "sem autenticacao" que {@code SpringCurrentUser}/{@code SecurityEnforcer}
 * tratam como anonimo.
 */
@Route("/admin")
@RequiresRole("ADMIN")
@AComponent(template = "<p>Area restrita</p>")
public class AdminPage {
}
