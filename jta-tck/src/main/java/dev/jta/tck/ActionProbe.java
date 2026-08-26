package dev.jta.tck;

import java.util.Map;

/**
 * Descreve um POST de acao HTMX ({@code /__jta/action/{selector}?action=...})
 * que {@link AbstractJtaTck} deve fazer contra o servidor de um adaptador.
 */
public record ActionProbe(String url, Map<String, String> formParams) {

    public ActionProbe(String url) {
        this(url, Map.of());
    }
}
