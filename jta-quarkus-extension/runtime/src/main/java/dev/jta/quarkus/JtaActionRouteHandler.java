package dev.jta.quarkus;

import dev.jta.runtime.ActionResult;
import dev.jta.runtime.CurrentUser;
import dev.jta.runtime.JtaActionDispatcher;
import dev.jta.runtime.csrf.CsrfRequest;
import dev.jta.runtime.csrf.CsrfTokenStore;
import dev.jta.runtime.session.JtaSession;
import io.quarkus.arc.Arc;
import io.quarkus.arc.ManagedContext;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler Vert.x do endpoint generico de acoes HTMX
 * ({@code POST /__jta/action/:selector}) - registrado uma unica vez pelo
 * modulo deployment (via {@code RouteBuildItem}, com body handler
 * instalado para {@code ctx.request().formAttributes()} funcionar).
 *
 * <p>Adaptador fino: mesma logica de {@code JtaActionController} (Spring) /
 * {@code JtaJavalin} (Javalin), traduzindo {@link ActionResult} para a
 * resposta Vert.x.
 */
final class JtaActionRouteHandler implements Handler<RoutingContext> {

    private static final Logger LOG = LoggerFactory.getLogger(JtaActionRouteHandler.class);

    @Override
    public void handle(RoutingContext ctx) {
        ManagedContext requestContext = Arc.container().requestContext();
        boolean activatedHere = !requestContext.isActive();
        if (activatedHere) {
            requestContext.activate();
        }
        try {
            String selector = ctx.pathParam("selector");
            String action = ctx.queryParams().get("action");
            if (selector == null || action == null || action.isBlank()) {
                ctx.response().setStatusCode(400).end();
                return;
            }

            JtaActionDispatcher dispatcher = Arc.container().instance(JtaActionDispatcher.class).get();
            CsrfTokenStore csrfTokenStore = Arc.container().instance(CsrfTokenStore.class).get();

            Map<String, String[]> params = new HashMap<>(toArrayMap(ctx.queryParams()));
            params.remove("action");
            params.putAll(toArrayMap(ctx.request().formAttributes()));

            CurrentUser user = QuarkusCurrentUser.current();
            JtaSession session = ctx.session() != null ? new VertxJtaSession(ctx.session()) : JtaSession.none();
            String cookieHeader = ctx.request().getHeader("Cookie");
            String csrfHeaderValue = ctx.request().getHeader(csrfTokenStore.headerName());
            CsrfRequest csrf = new CsrfRequest(cookieHeader, csrfHeaderValue);

            ActionResult result;
            try {
                result = dispatcher.dispatch(selector, action, params, user, session, csrf);
            } catch (IllegalArgumentException e) {
                ctx.response().setStatusCode(400).end();
                return;
            } catch (IllegalStateException e) {
                ctx.response().setStatusCode(500).end();
                return;
            }

            if (result instanceof ActionResult.Forbidden) {
                ctx.response().setStatusCode(403).end();
                return;
            }
            if (result instanceof ActionResult.NotFound) {
                // ver SECURITY.md, achado #1 - mesma logica de log dos outros adaptadores.
                LOG.warn("Tentativa de invocar acao nao declarada '{}' em '{}'", sanitizeForLog(action), selector);
                ctx.response().setStatusCode(404).end();
                return;
            }
            if (result instanceof ActionResult.Redirect redirect) {
                ctx.response().putHeader("HX-Redirect", redirect.path()).setStatusCode(200).end();
                return;
            }
            ActionResult.Rendered rendered = (ActionResult.Rendered) result;
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

    private static String sanitizeForLog(String value) {
        String cleaned = value.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "...(truncado)" : cleaned;
    }
}
