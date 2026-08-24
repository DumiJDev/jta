package dev.jta.standalone.fixtures;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

@Route("/hello/{name}")
@AComponent(
    template = "<p>Ola, {{ name }}! Contagem: {{ count }}</p>"
)
public class HelloPage {

    public String name = "";
    public int count = 0;

    public void increment() {
        count++;
    }
}
