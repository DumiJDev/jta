package dev.jta.standalone;

import dev.jta.core.SelectorDerivation;
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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de ponta a ponta contra um {@link JtaHttpServer} real (porta
 * aleatoria) - mesmo espirito do {@code ContadorIntegrationTest} em
 * jta-demo, so que sem framework/DI nenhum por baixo (so o HttpServer do
 * proprio JDK).
 */
class JtaHttpServerIntegrationTest {

    private static JtaHttpServer server;
    private static final HttpClient client = HttpClient.newHttpClient();

    @BeforeAll
    static void startServer() {
        server = JtaHttpServer.create(0, JtaStandaloneConfig.create());
        server.start();
    }

    @AfterAll
    static void stopServer() {
        server.stop();
    }

    private String baseUrl() {
        return "http://localhost:" + server.port();
    }

    private static final Pattern CSRF_TOKEN_PATTERN =
            Pattern.compile("hx-headers='\\{\"X-JTA-CSRF-Token\":\"([^\"]+)\"}'");

    private record Csrf(String cookie, String headerValue) {
    }

    /**
     * Fluxo real de CSRF nativo (ver SECURITY.md, achado #6): GET de uma
     * pagina emite a cookie assinada (mais a cookie de sessao) e embute o
     * token no {@code hx-headers} do {@code <body>}.
     */
    private Csrf fetchCsrf() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/contador")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
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
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");
        assertTrue(response.body().contains("data-jta-component=\"" + selector + "\""));
        assertTrue(response.body().contains(">0<"));
        assertTrue(response.headers().allValues("Set-Cookie").stream().anyMatch(v -> v.startsWith("jta_csrf=")));
        assertTrue(CSRF_TOKEN_PATTERN.matcher(response.body()).find());
    }

    @Test
    void acaoIncrementarReidrataEstadoEDevolveFragmento() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");
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
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");
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

    // --- SECURITY.md achado #6 (CSRF nativo) - regressao dedicada ---

    @Test
    void postSemCookieNemHeaderCsrfDevolve403() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }

    @Test
    void postComCookieMasSemHeaderDevolve403() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");
        Csrf csrf = fetchCsrf();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", csrf.cookie())
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }

    @Test
    void postComHeaderMasSemCookieDevolve403() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");
        Csrf csrf = fetchCsrf();
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("X-JTA-CSRF-Token", csrf.headerValue())
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }

    @Test
    void postComCookieForjadaComOutroSegredoDevolve403() throws Exception {
        // simula um token de outra origem: mesmo formato (token.assinatura),
        // mas a assinatura nao bate com o segredo real do servidor (o token
        // real "de outra origem" seria assinado com OUTRO segredo - aqui,
        // forjamos so trocando a assinatura por outra string valida em
        // formato, mas que nunca casaria com HMAC-SHA256(secret, token)).
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");
        Csrf csrf = fetchCsrf();
        String forgedCookie = "jta_csrf=" + csrf.headerValue() + ".assinatura-forjada-invalida";

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", forgedCookie)
                        .header("X-JTA-CSRF-Token", csrf.headerValue())
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }

    @Test
    void fluxoFelizGetDepoisPostComCookieETokenExtraidosDoHtmlFunciona() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");
        Csrf csrf = fetchCsrf();

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", csrf.cookie())
                        .header("X-JTA-CSRF-Token", csrf.headerValue())
                        .POST(HttpRequest.BodyPublishers.ofString("valor=1"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(">2<"));
    }

    // --- sessao agnostica ---

    @Test
    void atributoDeSessaoSobreviveAoPedidoSeguinteComMesmoCookie() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.SessaoProbe");

        HttpResponse<String> pagina = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/sessao")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String sessionCookie = pagina.headers().allValues("Set-Cookie").stream()
                .filter(v -> v.startsWith("JTASESSIONID="))
                .findFirst().orElseThrow(() -> new IllegalStateException("sem cookie de sessao: " + pagina.headers().allValues("Set-Cookie")));
        String sessionCookieValue = sessionCookie.split(";", 2)[0];
        String csrfCookieValue = pagina.headers().allValues("Set-Cookie").stream()
                .filter(v -> v.startsWith("jta_csrf=")).findFirst().orElseThrow().split(";", 2)[0];
        Matcher tokenMatcher = CSRF_TOKEN_PATTERN.matcher(pagina.body());
        assertTrue(tokenMatcher.find());
        String token = tokenMatcher.group(1);
        String combinedCookie = sessionCookieValue + "; " + csrfCookieValue;

        HttpResponse<String> primeiroIncremento = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", combinedCookie)
                        .header("X-JTA-CSRF-Token", token)
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, primeiroIncremento.statusCode());

        HttpResponse<String> segundoIncremento = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", combinedCookie)
                        .header("X-JTA-CSRF-Token", token)
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(200, segundoIncremento.statusCode());
        assertTrue(segundoIncremento.body().contains(">2<"));
    }

    @Test
    void sessoesDeCookiesDiferentesNaoCompartilhamEstado() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.SessaoProbe");

        HttpResponse<String> paginaA = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/sessao")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String sessionCookieA = paginaA.headers().allValues("Set-Cookie").stream()
                .filter(v -> v.startsWith("JTASESSIONID=")).findFirst().orElseThrow().split(";", 2)[0];
        String csrfCookieA = paginaA.headers().allValues("Set-Cookie").stream()
                .filter(v -> v.startsWith("jta_csrf=")).findFirst().orElseThrow().split(";", 2)[0];
        Matcher tokenMatcherA = CSRF_TOKEN_PATTERN.matcher(paginaA.body());
        assertTrue(tokenMatcherA.find());
        String tokenA = tokenMatcherA.group(1);

        client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", sessionCookieA + "; " + csrfCookieA)
                        .header("X-JTA-CSRF-Token", tokenA)
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // sessao B: nenhum cookie enviado - deve comecar do zero, nao herdar o "1" da sessao A.
        HttpResponse<String> paginaB = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/sessao")).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        String sessionCookieB = paginaB.headers().allValues("Set-Cookie").stream()
                .filter(v -> v.startsWith("JTASESSIONID=")).findFirst().orElseThrow().split(";", 2)[0];
        assertNotEquals(sessionCookieA, sessionCookieB);
        assertTrue(paginaB.body().contains("id=\"contagem\">0<"));
    }

    @Test
    void pathVariableEExtraidoSemNenhumRouterDeFramework() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/produtos/42")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(">42<"));
    }

    @Test
    void rotaInexistenteDevolve404() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/nao-existe")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    /**
     * Regressao: {@code Boolean.parseBoolean} so reconhecia {@code "true"},
     * mas um checkbox HTML sem {@code value} explicito envia {@code "on"}
     * quando marcado - o campo ficava sempre {@code false}.
     */
    @Test
    void checkboxMarcadoEnviandoOnPopulaCampoBooleanoComoTrue() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.ContaComCheckbox");
        Csrf csrf = fetchCsrf();

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=alternar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", csrf.cookie())
                        .header("X-JTA-CSRF-Token", csrf.headerValue())
                        .POST(HttpRequest.BodyPublishers.ofString("ativo=on"))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(">true<"),
                "checkbox marcado (value 'on', o default do HTML) deveria reidratar o campo como true");
    }

    @Test
    void checkboxAusenteDaRequisicaoNaoEAlterado() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.ContaComCheckbox");
        Csrf csrf = fetchCsrf();

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=alternar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", csrf.cookie())
                        .header("X-JTA-CSRF-Token", csrf.headerValue())
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(">false<"),
                "sem o parametro 'ativo' na requisicao (checkbox desmarcado nao envia nada), "
                        + "o campo deve manter o valor default, nao ser tratado como presente-mas-falso");
    }

    /**
     * Regressao: os catches do adaptador so cobriam
     * {@code IllegalArgumentException}/{@code IllegalStateException} - uma
     * {@code NullPointerException} vinda de uma acao do consumidor escapava
     * em vez de virar um 500 tratado.
     *
     * <p>Precisa do fluxo de CSRF (introduzido depois deste teste ter sido
     * escrito) para a requisicao chegar de facto a acao que lanca a NPE -
     * sem cookie/header validos, o pedido e rejeitado com 403 antes mesmo
     * de tentar invocar a acao.
     */
    @Test
    void excecaoInesperadaDeUmaAcaoVira500EmVezDeDerrubarAConexao() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.ComponenteInstavel");
        Csrf csrf = fetchCsrf();

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=explodir"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("Cookie", csrf.cookie())
                        .header("X-JTA-CSRF-Token", csrf.headerValue())
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
    }
}
