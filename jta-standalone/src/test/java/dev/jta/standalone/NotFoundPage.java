package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.ErrorPage;

/**
 * Fixture minimo para provar paginas de erro como componente registrado
 * (ver {@code dev.jta.core.ErrorPage}, {@code JtaErrorPageRenderer}) -
 * renderizado pelo {@link JtaHttpServer} quando nenhuma rota bate (404) ou
 * quando uma pagina lanca uma excecao nao tratada (500), sem precisar de
 * {@code @Route} nenhuma (nunca navegado diretamente).
 */
@AComponent(template = "<main><span id=\"erro-status\">{{ errorStatus }}</span>"
        + "<span id=\"erro-path\">{{ errorPath? }}</span></main>")
@ErrorPage(404)
public class NotFoundPage {

    public int errorStatus;
    public String errorPath;
}
