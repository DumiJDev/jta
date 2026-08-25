package dev.jta.quarkus;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.util.function.Supplier;

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

    /**
     * {@code SessionHandler} com {@code LocalSessionStore} (ja transitivo de
     * {@code quarkus-vertx-http}) - registrado pelo modulo deployment ANTES
     * dos handlers de pagina/acao, para que {@code RoutingContext.session()}
     * ja esteja populado quando {@code JtaPageRouteHandler}/
     * {@code JtaActionRouteHandler} rodarem (ver {@code VertxJtaSession}).
     */
    public Handler<RoutingContext> createSessionHandler(Supplier<Vertx> vertx) {
        return SessionHandler.create(LocalSessionStore.create(vertx.get()));
    }
}
