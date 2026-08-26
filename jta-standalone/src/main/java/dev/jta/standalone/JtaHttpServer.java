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
import dev.jta.runtime.JtaErrorPageRenderer;
import dev.jta.runtime.JtaPageDispatcher;
import dev.jta.runtime.JtaTemplateEngineFactory;
import dev.jta.runtime.PageResult;
import dev.jta.runtime.SseHub;
import dev.jta.runtime.csrf.CsrfRequest;
import dev.jta.runtime.csrf.CsrfTokenStore;
import dev.jta.runtime.csrf.CsrfTokenStoreFactory;
import dev.jta.runtime.session.InMemorySessionStore;
import dev.jta.runtime.session.JtaSession;
import dev.jta.runtime.session.SessionStore;
import dev.jta.runtime.upload.UploadedFile;
import gg.jte.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
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
 *
 * <p>SSE (um {@code @Sse}) usa o mesmo {@link SseHub} (jta-runtime,
 * agnostico de framework) que os outros adaptadores - como
 * {@code com.sun.net.httpserver} nao tem nenhum helper de SSE pronto,
 * este adaptador mantem a {@link HttpExchange} aberta (bloqueando a thread
 * virtual da conexao ate o cliente desconectar) e escreve cada evento
 * como {@code data: <html>\n\n}, dando flush apos cada escrita.
 */
public final class JtaHttpServer {

    private static final Logger LOG = LoggerFactory.getLogger(JtaHttpServer.class);
    private static final String ACTION_PREFIX = "/__jta/action/";

    private final HttpServer httpServer;
    private final SseHub sseHub;

    private JtaHttpServer(HttpServer httpServer, SseHub sseHub) {
        this.httpServer = httpServer;
        this.sseHub = sseHub;
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
     * {@link JtaTemplateEngineFactory} usa {@link TemplateEngine#createPrecompiled}
     * por padrao (carrega essas classes ja compiladas via classloader), e so
     * troca para o dev-loop (recompilacao sob demanda) se explicitamente
     * ligado - ver o javadoc daquela classe.
     */
    public static JtaHttpServer create(int port, JtaStandaloneConfig config) {
        try {
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            ComponentRegistry registry = ComponentRegistry.loadFromClasspath(classLoader);
            JtaConfig jtaConfig = JtaConfig.loadFromClasspath(classLoader);
            TemplateEngine templateEngine = JtaTemplateEngineFactory.create(jtaConfig, classLoader);

            CsrfTokenStore csrfTokenStore = config.csrfTokenStore() != null
                    ? config.csrfTokenStore() : CsrfTokenStoreFactory.create(jtaConfig);
            SessionStore sessionStore = config.sessionStore() != null
                    ? config.sessionStore()
                    : new InMemorySessionStore(Duration.ofMinutes(jtaConfig.getInt("session", "ttl_minutes", 30)));

            ComponentInvoker invoker = new ComponentInvoker(config.componentFactory());
            JtaPageDispatcher pageDispatcher = new JtaPageDispatcher(registry, invoker, templateEngine, jtaConfig, csrfTokenStore);
            JtaActionDispatcher actionDispatcher = new JtaActionDispatcher(registry, invoker, templateEngine, config.validator(), csrfTokenStore);
            JtaErrorPageRenderer errorPageRenderer = new JtaErrorPageRenderer(registry, invoker, templateEngine, jtaConfig);

            List<PageRoute> pageRoutes = new ArrayList<>();
            for (ComponentMetadata page : registry.pages()) {
                pageRoutes.add(new PageRoute(RoutePattern.compile(page.routePath()), page));
            }
            // A ordem por especificidade ("/produtos/novo" antes de
            // "/produtos/{id}") ja vem pronta de ComponentRegistry.pages(),
            // que passou a ordenar na fonte para todos os adaptadores - este
            // modulo nao precisa mais de a redefinir por conta propria.

            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext(ACTION_PREFIX, exchange -> handleAction(exchange, actionDispatcher, csrfTokenStore, sessionStore, jtaConfig, config, errorPageRenderer));

            SseHub sseHub = new SseHub(registry, invoker, templateEngine);
            List<ComponentMetadata> sseComponents = sseHub.sseComponents();
            if (!sseComponents.isEmpty()) {
                sseHub.start();
                for (ComponentMetadata sse : sseComponents) {
                    server.createContext(sse.ssePath(), exchange -> handleSse(exchange, sseHub, sse.ssePath(), config));
                }
            }

            server.createContext("/", exchange -> handlePage(exchange, pageRoutes, pageDispatcher, sessionStore, jtaConfig, config, errorPageRenderer));
            return new JtaHttpServer(server, sseHub);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao criar o HttpServer na porta " + port, e);
        }
    }

    public void start() {
        httpServer.start();
    }

    public void stop() {
        sseHub.stop();
        httpServer.stop(0);
    }

    public int port() {
        return httpServer.getAddress().getPort();
    }

    private record PageRoute(RoutePattern pattern, ComponentMetadata metadata) {
    }

    private static void handlePage(HttpExchange exchange, List<PageRoute> pageRoutes, JtaPageDispatcher dispatcher,
                                    SessionStore sessionStore, JtaConfig jtaConfig, JtaStandaloneConfig config,
                                    JtaErrorPageRenderer errorPageRenderer) throws IOException {
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
            String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
            JtaSession session = resolveSession(exchange, cookieHeader, sessionStore, jtaConfig);

            PageResult result;
            try {
                result = dispatcher.dispatch(route.metadata(), queryParams, pathVariables, user, session, cookieHeader);
            } catch (IllegalArgumentException e) {
                LOG.warn("Requisicao JTA invalida para a pagina '{}'", route.metadata().selector(), e);
                sendResponse(exchange, 400, "");
                return;
            } catch (RuntimeException e) {
                // RuntimeException, nao IllegalStateException: uma NPE
                // lancada por um metodo de template ou por um servico do dev
                // escapava daqui sem log nenhum deste lado.
                LOG.error("Falha interna ao renderizar a pagina '{}'", route.metadata().selector(), e);
                sendErrorResponse(exchange, 500, path, errorPageRenderer);
                return;
            }

            if (result instanceof PageResult.Forbidden) {
                sendErrorResponse(exchange, 403, path, errorPageRenderer);
                return;
            }
            PageResult.Rendered rendered = (PageResult.Rendered) result;
            if (rendered.csrfSetCookieHeader() != null) {
                // add (nao set) - nao pisar o Set-Cookie de sessao ja
                // adicionado por resolveSession, se houver (HttpExchange
                // suporta multiplos headers com o mesmo nome).
                exchange.getResponseHeaders().add("Set-Cookie", rendered.csrfSetCookieHeader());
            }
            sendResponse(exchange, 200, rendered.html());
            return;
        }
        sendErrorResponse(exchange, 404, path, errorPageRenderer);
    }

    private static void handleAction(HttpExchange exchange, JtaActionDispatcher dispatcher, CsrfTokenStore csrfTokenStore,
                                      SessionStore sessionStore, JtaConfig jtaConfig, JtaStandaloneConfig config,
                                      JtaErrorPageRenderer errorPageRenderer)
            throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!"POST".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "");
            return;
        }

        String remainder = path.substring(ACTION_PREFIX.length());
        if (remainder.isEmpty() || remainder.contains("/")) {
            sendErrorResponse(exchange, 404, path, errorPageRenderer);
            return;
        }
        String selector = remainder;

        Map<String, String[]> params = new HashMap<>(parseQueryString(exchange.getRequestURI().getRawQuery()));
        String action = firstValue(params, "action");
        params.remove("action");
        RequestBody requestBody = parseRequestBody(exchange);
        params.putAll(requestBody.params());

        if (action == null || action.isBlank()) {
            sendResponse(exchange, 400, "");
            return;
        }

        CurrentUser user = config.currentUserResolver().apply(exchange);
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        JtaSession session = resolveSession(exchange, cookieHeader, sessionStore, jtaConfig);
        String csrfHeaderValue = exchange.getRequestHeaders().getFirst(csrfTokenStore.headerName());
        CsrfRequest csrf = new CsrfRequest(cookieHeader, csrfHeaderValue);

        ActionResult result;
        try {
            result = dispatcher.dispatch(selector, action, params, user, session, csrf, requestBody.uploads());
        } catch (IllegalArgumentException e) {
            LOG.warn("Requisicao JTA invalida na acao '{}' de '{}'",
                    sanitizeForLog(action), sanitizeForLog(selector), e);
            sendResponse(exchange, 400, "");
            return;
        } catch (RuntimeException e) {
            LOG.error("Falha interna ao executar a acao '{}' de '{}'",
                    sanitizeForLog(action), sanitizeForLog(selector), e);
            sendErrorResponse(exchange, 500, path, errorPageRenderer);
            return;
        }

        if (result instanceof ActionResult.Forbidden) {
            sendErrorResponse(exchange, 403, path, errorPageRenderer);
            return;
        }
        if (result instanceof ActionResult.NotFound) {
            // ver SECURITY.md, achado #1 - mesma logica de log dos outros adaptadores.
            LOG.warn("Tentativa de invocar acao nao declarada '{}' em '{}'", sanitizeForLog(action), selector);
            sendErrorResponse(exchange, 404, path, errorPageRenderer);
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

    /**
     * Resolve a {@link JtaSession} a partir da cookie {@code [session] cookie_name}
     * (default {@code JTASESSIONID}) - cria uma nova sessao via
     * {@link SessionStore#getOrCreate} se a cookie estiver ausente/expirada, e
     * ja adiciona o {@code Set-Cookie} correspondente na resposta quando o id
     * resolvido difere do que veio na requisicao (sessao nova).
     *
     * <p>{@code com.sun.net.httpserver.HttpExchange} nao tem nenhuma nocao de
     * sessao por baixo - diferente do Spring (Servlet) e do Javalin (Jetty),
     * que reusam a sessao do proprio container, aqui precisamos de
     * {@link InMemorySessionStore} (jta-runtime) e parsing/escrita manual das
     * cookies.
     */
    private static JtaSession resolveSession(HttpExchange exchange, String cookieHeader, SessionStore sessionStore,
                                              JtaConfig jtaConfig) {
        String cookieName = jtaConfig.getString("session", "cookie_name", "JTASESSIONID");
        String requestedId = extractCookieValue(cookieHeader, cookieName);
        JtaSession session = sessionStore.getOrCreate(requestedId);
        if (requestedId == null || !requestedId.equals(session.id())) {
            int ttlMinutes = jtaConfig.getInt("session", "ttl_minutes", 30);
            boolean secure = jtaConfig.getBoolean("session", "secure", false);
            String sameSite = jtaConfig.getString("session", "same_site", "Lax");
            StringBuilder setCookie = new StringBuilder()
                    .append(cookieName).append('=').append(session.id())
                    .append("; Path=/; HttpOnly; SameSite=").append(sameSite)
                    .append("; Max-Age=").append(ttlMinutes * 60L);
            if (secure) {
                setCookie.append("; Secure");
            }
            // add (nao set) - ver SECURITY.md/plano de sessao: nao pisar
            // outros headers ja presentes na resposta.
            exchange.getResponseHeaders().add("Set-Cookie", setCookie.toString());
        }
        return session;
    }

    private static String extractCookieValue(String cookieHeader, String name) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String pair = part.trim();
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            if (pair.substring(0, eq).trim().equals(name)) {
                return pair.substring(eq + 1).trim();
            }
        }
        return null;
    }

    /**
     * Bridge do endpoint {@code @Sse} para {@link SseHub}: autoriza a
     * conexao contra {@code @RequiresRole} (mesma checagem de
     * {@link #handlePage}/{@link #handleAction}, agora tambem aplicada
     * aqui - ver SECURITY.md, achado #4), depois mantem a
     * {@link HttpExchange} aberta escrevendo cada evento do hub como
     * {@code data: <html>\n\n} (uma linha {@code data:} por linha do HTML,
     * formato padrao SSE) e dando flush apos cada escrita.
     *
     * <p>Sem callback de desconexao no {@code com.sun.net.httpserver}: a
     * thread da conexao (virtual - ver {@link #create}) bloqueia num
     * {@link CountDownLatch} ate a proxima tentativa de escrita falhar
     * (cliente desconectado), o mesmo atraso ja presente no modelo de
     * polling por intervalo do {@code @Sse} (ver {@link dev.jta.core.Sse}).
     */
    private static void handleSse(HttpExchange exchange, SseHub hub, String path, JtaStandaloneConfig config)
            throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "");
            return;
        }

        CurrentUser user = config.currentUserResolver().apply(exchange);
        if (!hub.isAuthorized(path, user)) {
            sendResponse(exchange, 403, "");
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0); // 0 = chunked, tamanho desconhecido - conexao fica aberta
        OutputStream out = exchange.getResponseBody();

        CountDownLatch disconnected = new CountDownLatch(1);
        SseHub.Subscriber subscriber = data -> {
            try {
                String event = "data: " + data.replace("\n", "\ndata: ") + "\n\n";
                out.write(event.getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException e) {
                disconnected.countDown();
                throw e;
            }
        };
        hub.subscribe(path, subscriber);
        try {
            disconnected.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            hub.unsubscribe(path, subscriber);
            exchange.close();
        }
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

    private record RequestBody(Map<String, String[]> params, Map<String, UploadedFile> uploads) {
        static final RequestBody EMPTY = new RequestBody(Map.of(), Map.of());
    }

    /**
     * Le o corpo da requisicao UMA VEZ e decide como interpreta-lo pelo
     * {@code Content-Type}: {@code application/x-www-form-urlencoded}
     * (comportamento pre-existente) ou {@code multipart/form-data} (ver
     * {@link MultipartParser} - com.sun.net.httpserver nao tem parsing de
     * multipart embutido, ao contrario dos outros 3 adaptadores). Partes
     * sem {@code filename} (campos de texto comuns enviados via
     * multipart) viram entradas normais de {@code params}; partes COM
     * {@code filename} viram {@link UploadedFile}, indexadas pelo nome do
     * campo (ver {@code ComponentMetadata#uploadFields}).
     */
    private static RequestBody parseRequestBody(HttpExchange exchange) throws IOException {
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null) {
            return RequestBody.EMPTY;
        }
        String lowerContentType = contentType.toLowerCase();
        if (lowerContentType.startsWith("application/x-www-form-urlencoded")) {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            return new RequestBody(parseEncodedForm(body), Map.of());
        }
        if (lowerContentType.startsWith("multipart/form-data")) {
            String boundary = MultipartParser.extractBoundary(contentType);
            if (boundary == null) {
                return RequestBody.EMPTY;
            }
            byte[] body = exchange.getRequestBody().readAllBytes();
            Map<String, List<String>> textFields = new HashMap<>();
            Map<String, UploadedFile> uploads = new HashMap<>();
            for (MultipartParser.Part part : MultipartParser.parse(body, boundary)) {
                if (part.isFile()) {
                    uploads.put(part.name(), new UploadedFile(part.filename(), part.contentType(), part.content()));
                } else {
                    textFields.computeIfAbsent(part.name(), k -> new ArrayList<>())
                            .add(new String(part.content(), StandardCharsets.UTF_8));
                }
            }
            Map<String, String[]> params = new HashMap<>();
            textFields.forEach((key, values) -> params.put(key, values.toArray(new String[0])));
            return new RequestBody(params, uploads);
        }
        return RequestBody.EMPTY;
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

    /**
     * Tenta renderizar o componente {@code @ErrorPage} registrado para
     * {@code status} (ver {@link JtaErrorPageRenderer}) antes de devolver a
     * resposta - sem nenhum componente registrado, cai no comportamento
     * pre-existente (corpo vazio), mesmo padrao dos outros adaptadores
     * (Spring: {@code JtaExceptionHandler}/{@code JtaRouteRegistrar}).
     */
    private static void sendErrorResponse(HttpExchange exchange, int status, String path,
                                           JtaErrorPageRenderer errorPageRenderer) throws IOException {
        String html = errorPageRenderer.render(status, path, null).orElse("");
        sendResponse(exchange, status, html);
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
