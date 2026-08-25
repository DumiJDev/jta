package dev.jta.standalone.fixtures;

import dev.jta.core.AComponent;
import dev.jta.core.Route;
import dev.jta.core.Use;

@Route("/slot-consumer")
@AComponent(template = "<div><slot-card>{{ titulo }}</slot-card><slot-card/></div>")
@Use(type = SlotCard.class, as = "slot-card")
public class SlotConsumerPage {
    public String titulo = "Ola";
}
