package dev.jta.javalin;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import dev.jta.runtime.ActionResult;
import dev.jta.runtime.ComponentInvoker;
import dev.jta.runtime.CurrentUser;
import dev.jta.runtime.JtaActionDispatcher;
import dev.jta.runtime.JtaPageDispatcher;
import dev.jta.runtime.PageResult;
import dev.jta.runtime.csrf.CsrfRequest;
import dev.jta.runtime.csrf.CsrfTokenStore;
import dev.jta.runtime.csrf.CsrfTokenStoreFactory;
import gg.jte.TemplateEngine;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Liga o JTA a um {@link Javalin} ja criado: registra um GET por
 * {@code @Route} e o endpoint generico de acoes HTMX
 * ({@code POST /__jta/action/{selector}}).
 *
 * <p><b>Adaptador fino:</b> so extrai path params/query params/form data do
 * {@link Context} do Javalin e traduz {@link PageResult}/{@link ActionResult}
 * de volta para respostas Javalin - toda a orquestracao (autorizacao,
 * allowlist de acoes, reidratacao de estado, render) esta em
 * {@link JtaPageDispatcher}/{@link JtaActionDispatcher} (jta-runtime,
 * agnostico de framework). Mesmo padrao de {@code JtaRouteRegistrar}/
 * {@code JtaActionController} em jta-spring-boot-starter.
 *
 * <p>Javalin usa nativamente a sintaxe {@code {param}} para path variables -
 * o mesmo formato de {@code @Route}, sem necessidade de conversao.
 *
 * <p><b>{@code app.unsafe.routes} em vez de {@code app.get}/{@code app.post}:</b>
 * a partir do Javalin 6/7, a classe {@link Javalin} nao expoe mais metodos
 * de roteamento diretamente - registrar rotas normalmente acontece dentro
 * do callback de {@code Javalin.create(cfg -> ...)}, via {@code cfg.routes}
 * ({@code RoutesConfig}, o unico tipo que implementa
 * {@code JavalinDefaultRoutingApi}). Como este adaptador recebe um
 * {@link Javalin} ja criado (nao controla a chamada a {@code create}), a
 * unica forma de acrescentar rotas depois e via {@code app.unsafe.routes}
 * (o mesmo {@code RoutesConfig}, exposto como escape hatch em
 * {@code JavalinState}).
 */
public final class JtaJavalin {

    private static final Logger LOG = LoggerFactory.getLogger(JtaJavalin.class);

    private JtaJavalin() {
    }

    /**
     * Carrega o {@link ComponentRegistry}/{@link JtaConfig} do classpath,
     * cria o {@link TemplateEngine} pre-compilado e registra todas as rotas
     * JTA no {@code app} informado.
     *
     * <p><b>Importante:</b> tal como no starter Spring, o consumidor
     * precisa configurar o {@code jte-maven-plugin} (goal {@code precompile})
     * no seu proprio build apontado para o output do {@code jta-processor} -
     * {@link TemplateEngine#createPrecompiled} carrega essas classes ja
     * compiladas via classloader, nao recompila {@code .jte} em runtime.
     */
    public static void register(Javalin app, JtaJavalinConfig config) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ComponentRegistry registry = ComponentRegistry.loadFromClasspath(classLoader);
        JtaConfig jtaConfig = JtaConfig.loadFromClasspath(classLoader);
        TemplateEngine templateEngine = TemplateEngine.createPrecompiled(gg.jte.ContentType.Html);

        CsrfTokenStore csrfTokenStore = config.csrfTokenStore() != null
                ? config.csrfTokenStore() : CsrfTokenStoreFactory.create(jtaConfig);

        ComponentInvoker invoker = new ComponentInvoker(config.componentFactory());
        JtaPageDispatcher pageDispatcher = new JtaPageDispatcher(registry, invoker, templateEngine, jtaConfig, csrfTokenStore);
        JtaActionDispatcher actionDispatcher = new JtaActionDispatcher(registry, invoker, templateEngine, config.validator(), csrfTokenStore);

        for (ComponentMetadata page : registry.pages()) {
            app.unsafe.routes.get(page.routePath(), ctx -> handlePage(ctx, page, pageDispatcher, config));
        }
        app.unsafe.routes.post("/__jta/action/{selector}", ctx -> handleAction(ctx, actionDispatcher, csrfTokenStore, config));
    }

    /** Atalho de conveniencia com a configuracao default (sem DI, sem autenticacao). */
    public static void register(Javalin app) {
        register(app, JtaJavalinConfig.create());
    }

    private static void handlePage(Context ctx, ComponentMetadata metadata, JtaPageDispatcher dispatcher,
                                    JtaJavalinConfig config) {
        Map<String, String[]> queryParams = toArrayMap(ctx.queryParamMap());
        Map<String, String> pathVariables = ctx.pathParamMap();
        CurrentUser user = config.currentUserResolver().apply(ctx);
        var session = new JavalinJtaSession(ctx.req().getSession(true));
        String cookieHeader = ctx.req().getHeader("Cookie");

        PageResult result;
        try {
            result = dispatcher.dispatch(metadata, queryParams, pathVariables, user, session, cookieHeader);
        } catch (IllegalArgumentException e) {
            LOG.warn("Requisicao JTA invalida para a pagina '{}'", metadata.selector(), e);
            ctx.status(400);
            return;
        } catch (RuntimeException e) {
            // RuntimeException, nao IllegalStateException: uma NPE lancada
            // por um metodo de template ou por um servico injetado do dev
            // escapava daqui e batia na pagina de erro default do Jetty, em
            // vez do tratamento do JTA - e sem nenhum log deste lado.
            LOG.error("Falha interna ao renderizar a pagina '{}'", metadata.selector(), e);
            ctx.status(500);
            return;
        }

        if (result instanceof PageResult.Forbidden) {
            ctx.status(403);
            return;
        }
        PageResult.Rendered rendered = (PageResult.Rendered) result;
        if (rendered.csrfSetCookieHeader() != null) {
            ctx.addHeader("Set-Cookie", rendered.csrfSetCookieHeader());
        }
        ctx.html(rendered.html());
    }

    private static void handleAction(Context ctx, JtaActionDispatcher dispatcher, CsrfTokenStore csrfTokenStore,
                                      JtaJavalinConfig config) {
        String selector = ctx.pathParam("selector");
        String action = ctx.queryParam("action");
        if (action == null || action.isBlank()) {
            ctx.status(400);
            return;
        }

        Map<String, String[]> params = new HashMap<>(toArrayMap(ctx.queryParamMap()));
        params.putAll(toArrayMap(ctx.formParamMap()));
        CurrentUser user = config.currentUserResolver().apply(ctx);
        var session = new JavalinJtaSession(ctx.req().getSession(true));
        String cookieHeader = ctx.req().getHeader("Cookie");
        String csrfHeaderValue = ctx.req().getHeader(csrfTokenStore.headerName());
        CsrfRequest csrf = new CsrfRequest(cookieHeader, csrfHeaderValue);

        ActionResult result;
        try {
            result = dispatcher.dispatch(selector, action, params, user, session, csrf);
        } catch (IllegalArgumentException e) {
            LOG.warn("Requisicao JTA invalida na acao '{}' de '{}'",
                    sanitizeForLog(action), sanitizeForLog(selector), e);
            ctx.status(400);
            return;
        } catch (RuntimeException e) {
            LOG.error("Falha interna ao executar a acao '{}' de '{}'",
                    sanitizeForLog(action), sanitizeForLog(selector), e);
            ctx.status(500);
            return;
        }

        if (result instanceof ActionResult.Forbidden) {
            ctx.status(403);
            return;
        }
        if (result instanceof ActionResult.NotFound) {
            // ver SECURITY.md, achado #1 - mesma logica de log do JtaActionController (Spring).
            LOG.warn("Tentativa de invocar acao nao declarada '{}' em '{}'", sanitizeForLog(action), selector);
            ctx.status(404);
            return;
        }
        if (result instanceof ActionResult.Redirect redirect) {
            ctx.header("HX-Redirect", redirect.path());
            ctx.status(200);
            return;
        }
        ActionResult.Rendered rendered = (ActionResult.Rendered) result;
        ctx.html(rendered.html());
    }

    private static Map<String, String[]> toArrayMap(Map<String, List<String>> source) {
        Map<String, String[]> result = new HashMap<>();
        source.forEach((key, values) -> result.put(key, values.toArray(new String[0])));
        return result;
    }

    private static String sanitizeForLog(String value) {
        String cleaned = value.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "...(truncado)" : cleaned;
    }
}
