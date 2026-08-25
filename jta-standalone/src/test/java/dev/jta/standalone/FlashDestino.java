package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

@Route("/flash-destino")
@AComponent(template = "<main><span id=\"flash\">{{ flashSuccess? }}</span></main>")
public class FlashDestino {

    public String flashSuccess;
}
