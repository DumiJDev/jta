package dev.jta.demo;

import dev.jta.core.Layout;

/**
 * Substitui o antigo hack de {@code Layout.NAV} (constante Java
 * concatenada em cada template) pelo mecanismo de verdade do framework:
 * {@code @Layout} + {@code <router-outlet/>}. Toda pagina que usar
 * {@code @Route(layout = SiteLayout.class)} tem seu HTML renderizado
 * inserido no lugar do {@code <router-outlet/>} - a composicao acontece
 * em runtime (ver {@code JtaRouteRegistrar} no starter).
 *
 * <p>Usa {@code templateUrl()} (arquivo externo em
 * {@code src/main/resources/jta-templates/dev/jta/demo/SiteLayout.jta})
 * em vez de inline - o primeiro uso de verdade dessa feature no proprio
 * demo (antes so era exercitada em testes descartaveis).
 */
@Layout(templateUrl = "SiteLayout.jta")
public class SiteLayout {
}
