package dev.jta.javalin;

import dev.jta.core.SelectorDerivation;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de ponta a ponta contra um {@link Javalin} real (servidor Jetty
 * embutido, porta aleatoria) - mesmo espirito do
 * {@code ContadorIntegrationTest} em jta-demo, so que sem nenhum container
 * de DI (Javalin puro).
 */
class JtaJavalinIntegrationTest {

    private static Javalin app;
    private static int port;
    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() {
        app = Javalin.create();
        JtaJavalin.register(app);
        app.start(0);
        port = app.port();
    }

    @AfterAll
    static void stopServer() {
        app.stop();
    }

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static final Pattern CSRF_TOKEN_PATTERN =
            Pattern.compile("hx-headers='\\{\"X-JTA-CSRF-Token\":\"([^\"]+)\"}'");

    private record Csrf(String cookie, String headerValue) {
    }

    /**
     * Fluxo real de CSRF nativo (ver SECURITY.md, achado #6): GET de uma
     * pagina emite a cookie assinada e embute o token no {@code hx-headers}
     * do {@code <body>} - extrai os dois para uma acao POST subsequente
     * poder provar que teve acesso ao HTML da pagina.
     */
    private Csrf fetchCsrf() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/contador")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        // a resposta tem DOIS Set-Cookie (JTASESSIONID/JSESSIONID do
        // container + jta_csrf) - filtra especificamente o de CSRF em vez
        // de assumir que e o primeiro.
        String setCookie = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("jta_csrf="))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GET de pagina nao emitiu Set-Cookie de CSRF: "
                        + response.headers().allValues("Set-Cookie")));
        String cookie = setCookie.split(";", 2)[0];
        Matcher matcher = CSRF_TOKEN_PATTERN.matcher(response.body());
        if (!matcher.find()) {
            throw new IllegalStateException("token CSRF nao encontrado no hx-headers: " + response.body());
        }
        return new Csrf(cookie, matcher.group(1));
    }

    @Test
    void paginaContadorRendersHtmlComHtmxEEmiteCookieDeCsrf() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/contador")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("htmx.org"));
        String selector = SelectorDerivation.derive("dev.jta.javalin.Contador");
        assertTrue(response.body().contains("data-jta-component=\"" + selector + "\""));
        assertTrue(response.body().contains(">0<"));
        assertTrue(response.headers().firstValue("Set-Cookie").isPresent());
        assertTrue(CSRF_TOKEN_PATTERN.matcher(response.body()).find());
    }

    @Test
    void acaoIncrementarReidrataEstadoEDevolveFragmento() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.javalin.Contador");
        Csrf csrf = fetchCsrf();

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", csrf.cookie())
                        .header("X-JTA-CSRF-Token", csrf.headerValue())
                        .POST(HttpRequest.BodyPublishers.ofString("valor=5"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(">6<"));
        assertFalse(response.body().contains("<!DOCTYPE html>"));
    }

    @Test
    void acaoNaoDeclaradaDevolve404() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.javalin.Contador");
        Csrf csrf = fetchCsrf();

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=hashCode"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", csrf.cookie())
                        .header("X-JTA-CSRF-Token", csrf.headerValue())
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void acaoSemCookieNemHeaderCsrfDevolve403() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.javalin.Contador");

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString("valor=5"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(403, response.statusCode());
    }
}
