package dev.jta.standalone.fixtures;

import dev.jta.core.AComponent;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;

@Route("/admin")
@RequiresRole("ADMIN")
@AComponent(template = "<p>Area restrita</p>")
public class AdminPage {
}
