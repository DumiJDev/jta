package dev.jta.quarkus;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;

/**
 * Recorder usado pelo modulo deployment para produzir os handlers Vert.x
 * de cada {@code RouteBuildItem} - a instanciacao real (o construtor de
 * {@link JtaPageRouteHandler}/{@link JtaActionRouteHandler}) acontece na
 * fase de {@code RUNTIME_INIT} da aplicacao, nao durante o build.
 */
@Recorder
public class JtaRecorder {

    public Handler<RoutingContext> createPageHandler(String selector) {
        return new JtaPageRouteHandler(selector);
    }

    public Handler<RoutingContext> createActionHandler() {
        return new JtaActionRouteHandler();
    }
}
