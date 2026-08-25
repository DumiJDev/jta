package dev.jta.quarkus;

import dev.jta.runtime.CurrentUser;
import dev.jta.runtime.SseHub;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;

/**
 * Handler Vert.x registrado (via {@code RouteBuildItem}, no modulo
 * deployment) para um endpoint {@code @Sse} - bridgeia o
 * {@link HttpServerResponse} cru do Vert.x (chunked, sem RESTEasy
 * Reactive/{@code Multi} envolvido - mesmo estilo raw-Vert.x de
 * {@link JtaPageRouteHandler}/{@link JtaActionRouteHandler}, os outros
 * dois handlers deste modulo) para {@link SseHub} (jta-runtime, agnostico
 * de framework).
 *
 * <p>Ativa o {@code ManagedContext} do Arc so para resolver
 * {@link SseHub}/{@link CurrentUser} na autorizacao inicial da conexao
 * (mesmo motivo de {@link JtaPageRouteHandler}) - depois disso, a conexao
 * fica aberta assincronamente sobre o event loop do Vert.x, sem manter o
 * contexto de requisicao do Arc ativo (o broadcast periodico roda na
 * thread do agendador de {@link SseHub}, nao no event loop da requisicao).
 */
final class JtaSseRouteHandler implements Handler<RoutingContext> {

    private final String path;

    JtaSseRouteHandler(String path) {
        this.path = path;
    }

    @Override
    public void handle(RoutingContext ctx) {
        ManagedContext requestContext = Arc.container().requestContext();
        boolean activatedHere = !requestContext.isActive();
        SseHub hub;
        CurrentUser user;
        if (activatedHere) {
            requestContext.activate();
        }
        try {
            hub = Arc.container().instance(SseHub.class).get();
            user = QuarkusCurrentUser.current();
        } finally {
            if (activatedHere) {
                requestContext.terminate();
            }
        }

        if (!hub.isAuthorized(path, user)) {
            ctx.response().setStatusCode(403).end();
            return;
        }

        HttpServerResponse response = ctx.response();
        response.putHeader("Content-Type", "text/event-stream; charset=utf-8");
        response.putHeader("Cache-Control", "no-cache");
        response.setChunked(true);

        SseHub.Subscriber subscriber = data -> {
            String event = "data: " + data.replace("\n", "\ndata: ") + "\n\n";
            response.write(event);
        };
        hub.subscribe(path, subscriber);
        response.closeHandler(v -> hub.unsubscribe(path, subscriber));
        response.exceptionHandler(t -> hub.unsubscribe(path, subscriber));
    }
}
