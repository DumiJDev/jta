package dev.jta.standalone.fixtures;

import dev.jta.core.AComponent;

@AComponent(selector = "slot-card", template = "<div class=\"card\"><slot>sem conteudo</slot></div>")
public class SlotCard {
}
