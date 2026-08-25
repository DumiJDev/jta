package dev.jta.test.fixture;

import dev.jta.core.AComponent;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;

/**
 * Fixture para exercitar {@link dev.jta.test.TestCurrentUser} contra
 * {@code @RequiresRole} via {@link dev.jta.test.JtaTestHarness#renderPage}.
 */
@Route("/admin")
@RequiresRole("ADMIN")
@AComponent(template = "<main>painel restrito</main>")
public class PainelAdmin {
}
