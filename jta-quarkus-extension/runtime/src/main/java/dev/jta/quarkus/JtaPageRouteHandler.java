package dev.jta.quarkus;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.runtime.CurrentUser;
import dev.jta.runtime.JtaPageDispatcher;
import dev.jta.runtime.PageResult;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler Vert.x registrado (via {@code RouteBuildItem}, no modulo
 * deployment) para um {@code @Route} especifico - resolvido pelo
 * {@code selector} em vez de carregar a {@link ComponentMetadata} inteira,
 * para nao precisar bytecode-record-ar um record complexo atraves do
 * {@code @Recorder}.
 *
 * <p><b>Adaptador fino:</b> so extrai path params/query params do
 * {@link RoutingContext} e traduz {@link PageResult} de volta para a
 * resposta Vert.x - toda a orquestracao esta em {@link JtaPageDispatcher}
 * (jta-runtime, agnostico de framework). Mesmo padrao de
 * {@code JtaRouteRegistrar} (Spring) / {@code JtaJavalin} (Javalin).
 *
 * <p>Ativa o {@code ManagedContext} de requisicao do Arc manualmente
 * porque esta rota e registrada como um {@code Handler<RoutingContext>}
 * Vert.x puro (via {@code RouteBuildItem}), sem passar pelo mecanismo de
 * reactive routes do Quarkus que normalmente cuida disso - sem isto,
 * beans {@code @ApplicationScoped}/{@code @RequestScoped} (ex:
 * {@code SecurityIdentity}) nao resolveriam corretamente.
 */
final class JtaPageRouteHandler implements Handler<RoutingContext> {

    private final String selector;

    JtaPageRouteHandler(String selector) {
        this.selector = selector;
    }

    @Override
    public void handle(RoutingContext ctx) {
        ManagedContext requestContext = Arc.container().requestContext();
        boolean activatedHere = !requestContext.isActive();
        if (activatedHere) {
            requestContext.activate();
        }
        try {
            ComponentRegistry registry = Arc.container().instance(ComponentRegistry.class).get();
            ComponentMetadata metadata = registry.bySelector(selector);
            JtaPageDispatcher dispatcher = Arc.container().instance(JtaPageDispatcher.class).get();

            Map<String, String[]> queryParams = toArrayMap(ctx.queryParams());
            Map<String, String> pathVariables = new HashMap<>(ctx.pathParams());
            CurrentUser user = QuarkusCurrentUser.current();

            PageResult result;
            try {
                result = dispatcher.dispatch(metadata, queryParams, pathVariables, user);
            } catch (IllegalArgumentException e) {
                ctx.response().setStatusCode(400).end();
                return;
            } catch (IllegalStateException e) {
                ctx.response().setStatusCode(500).end();
                return;
            }

            if (result instanceof PageResult.Forbidden) {
                ctx.response().setStatusCode(403).end();
                return;
            }
            PageResult.Rendered rendered = (PageResult.Rendered) result;
            ctx.response().putHeader("Content-Type", "text/html; charset=utf-8").end(rendered.html());
        } finally {
            if (activatedHere) {
                requestContext.terminate();
            }
        }
    }

    private static Map<String, String[]> toArrayMap(MultiMap multiMap) {
        Map<String, String[]> result = new HashMap<>();
        for (String name : multiMap.names()) {
            result.put(name, multiMap.getAll(name).toArray(new String[0]));
        }
        return result;
    }
}
