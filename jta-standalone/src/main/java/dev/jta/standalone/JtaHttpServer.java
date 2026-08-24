package dev.jta.standalone;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import dev.jta.runtime.ActionResult;
import dev.jta.runtime.ComponentInvoker;
import dev.jta.runtime.CurrentUser;
import dev.jta.runtime.JtaActionDispatcher;
import dev.jta.runtime.JtaPageDispatcher;
import dev.jta.runtime.PageResult;
import gg.jte.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Runner zero-dependencias do JTA: serve componentes/paginas via
 * {@link HttpServer} embutido no JDK, sem framework web/DI nenhum por
 * baixo.
 *
 * <p><b>Adaptador fino:</b> so faz o casamento de padrao de rota (via
 * {@link RoutePattern} - o unico bloco de routing genuino desta entrega),
 * extrai query string/form data do {@link HttpExchange} e traduz
 * {@link PageResult}/{@link ActionResult} de volta para a resposta HTTP -
 * toda a orquestracao (autorizacao, allowlist de acoes, reidratacao de
 * estado, render) esta em {@link JtaPageDispatcher}/{@link JtaActionDispatcher}
 * (jta-runtime, agnostico de framework). Mesmo padrao dos outros
 * adaptadores (Spring, Javalin, Quarkus).
 */
public final class JtaHttpServer {

    private static final Logger LOG = LoggerFactory.getLogger(JtaHttpServer.class);
    private static final String ACTION_PREFIX = "/__jta/action/";

    private final HttpServer httpServer;

    private JtaHttpServer(HttpServer httpServer) {
        this.httpServer = httpServer;
    }

    /**
     * Carrega o {@link ComponentRegistry}/{@link JtaConfig} do classpath,
     * cria o {@link TemplateEngine} pre-compilado e monta um
     * {@link HttpServer} pronto para {@link #start()} - nao inicia
     * sozinho, para o consumidor poder registrar outros contexts antes.
     *
     * <p><b>Importante:</b> tal como nos outros adaptadores, o consumidor
     * precisa configurar o {@code jte-maven-plugin} (goal {@code precompile})
     * no seu proprio build apontado para o output do {@code jta-processor} -
     * {@link TemplateEngine#createPrecompiled} carrega essas classes ja
     * compiladas via classloader, nao recompila {@code .jte} em runtime.
     */
    public static JtaHttpServer create(int port, JtaStandaloneConfig config) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            ComponentRegistry registry = ComponentRegistry.loadFromClasspath(classLoader);
            JtaConfig jtaConfig = JtaConfig.loadFromClasspath(classLoader);
            TemplateEngine templateEngine = TemplateEngine.createPrecompiled(gg.jte.ContentType.Html);

            ComponentInvoker invoker = new ComponentInvoker(config.componentFactory());
            JtaPageDispatcher pageDispatcher = new JtaPageDispatcher(registry, invoker, templateEngine, jtaConfig);
            JtaActionDispatcher actionDispatcher = new JtaActionDispatcher(registry, invoker, templateEngine, config.validator());

            List<PageRoute> pageRoutes = new ArrayList<>();
            for (ComponentMetadata page : registry.pages()) {
                pageRoutes.add(new PageRoute(RoutePattern.compile(page.routePath()), page));
            }
            // padroes com menos variaveis primeiro, ex: "/produtos/novo" antes de "/produtos/{id}".
            pageRoutes.sort(Comparator.comparingInt(route -> route.pattern().variableCount()));

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext(ACTION_PREFIX, exchange -> handleAction(exchange, actionDispatcher, config));
            server.createContext("/", exchange -> handlePage(exchange, pageRoutes, pageDispatcher, config));
            return new JtaHttpServer(server);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao criar o HttpServer na porta " + port, e);
        }
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        httpServer.stop(0);
    }

    public int port() {
        return httpServer.getAddress().getPort();
    }

    private record PageRoute(RoutePattern pattern, ComponentMetadata metadata) {
    }

    private static void handlePage(HttpExchange exchange, List<PageRoute> pageRoutes, JtaPageDispatcher dispatcher,
                                    JtaStandaloneConfig config) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "");
            return;
        }

        String path = exchange.getRequestURI().getPath();
        for (PageRoute route : pageRoutes) {
            Map<String, String> pathVariables = route.pattern().match(path);
            if (pathVariables == null) {
                continue;
            }

            Map<String, String[]> queryParams = parseQueryString(exchange.getRequestURI().getRawQuery());
            CurrentUser user = config.currentUserResolver().apply(exchange);

            PageResult result;
            try {
                result = dispatcher.dispatch(route.metadata(), queryParams, pathVariables, user);
            } catch (IllegalArgumentException e) {
                sendResponse(exchange, 400, "");
                return;
            } catch (IllegalStateException e) {
                sendResponse(exchange, 500, "");
                return;
            }

            if (result instanceof PageResult.Forbidden) {
                sendResponse(exchange, 403, "");
                return;
            }
            PageResult.Rendered rendered = (PageResult.Rendered) result;
            sendResponse(exchange, 200, rendered.html());
            return;
        }
        sendResponse(exchange, 404, "");
    }

    private static void handleAction(HttpExchange exchange, JtaActionDispatcher dispatcher, JtaStandaloneConfig config)
            throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "");
            return;
        }

        String remainder = exchange.getRequestURI().getPath().substring(ACTION_PREFIX.length());
        if (remainder.isEmpty() || remainder.contains("/")) {
            sendResponse(exchange, 404, "");
            return;
        }
        String selector = remainder;

        Map<String, String[]> params = new HashMap<>(parseQueryString(exchange.getRequestURI().getRawQuery()));
        String action = firstValue(params, "action");
        params.remove("action");
        params.putAll(parseFormBody(exchange));

        if (action == null || action.isBlank()) {
            sendResponse(exchange, 400, "");
            return;
        }

        CurrentUser user = config.currentUserResolver().apply(exchange);

        ActionResult result;
        try {
            result = dispatcher.dispatch(selector, action, params, user);
        } catch (IllegalArgumentException e) {
            sendResponse(exchange, 400, "");
            return;
        } catch (IllegalStateException e) {
            sendResponse(exchange, 500, "");
            return;
        }

        if (result instanceof ActionResult.Forbidden) {
            sendResponse(exchange, 403, "");
            return;
        }
        if (result instanceof ActionResult.NotFound) {
            // ver SECURITY.md, achado #1 - mesma logica de log dos outros adaptadores.
            LOG.warn("Tentativa de invocar acao nao declarada '{}' em '{}'", sanitizeForLog(action), selector);
            sendResponse(exchange, 404, "");
            return;
        }
        if (result instanceof ActionResult.Redirect redirect) {
            exchange.getResponseHeaders().set("HX-Redirect", redirect.path());
            sendResponse(exchange, 200, "");
            return;
        }
        ActionResult.Rendered rendered = (ActionResult.Rendered) result;
        sendResponse(exchange, 200, rendered.html());
    }

    private static String firstValue(Map<String, String[]> params, String key) {
        String[] values = params.get(key);
        return values == null || values.length == 0 ? null : values[0];
    }

    private static Map<String, String[]> parseQueryString(String rawQuery) {
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        return parseEncodedForm(rawQuery);
    }

    private static Map<String, String[]> parseFormBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/x-www-form-urlencoded")) {
            return Map.of();
        }
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseEncodedForm(body);
    }

    private static Map<String, String[]> parseEncodedForm(String encoded) {
        Map<String, List<String>> collected = new HashMap<>();
        for (String pair : encoded.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawKey = eq < 0 ? pair : pair.substring(0, eq);
            String rawValue = eq < 0 ? "" : pair.substring(eq + 1);
            String key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
            String value = URLDecoder.decode(rawValue, StandardCharsets.UTF_8);
            collected.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
        }
        Map<String, String[]> result = new HashMap<>();
        collected.forEach((key, values) -> result.put(key, values.toArray(new String[0])));
        return result;
    }

    private static void sendResponse(HttpExchange exchange, int status, String html) throws IOException {
        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        if (bytes.length == 0) {
            // HttpExchange#sendResponseHeaders trata responseLength=0 como
            // "chunked, tamanho desconhecido ainda" - -1 e o valor correto
            // para "sem corpo nenhum" (ver javadoc do metodo).
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String sanitizeForLog(String value) {
        String cleaned = value.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "...(truncado)" : cleaned;
    }
}
