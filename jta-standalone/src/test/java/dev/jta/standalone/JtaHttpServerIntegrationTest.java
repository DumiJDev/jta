package dev.jta.standalone;

import dev.jta.core.SelectorDerivation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void paginaContadorRendersHtmlComHtmx() throws Exception {
        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/contador")).GET().build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("htmx.org"));
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");
        assertTrue(response.body().contains("data-jta-component=\"" + selector + "\""));
        assertTrue(response.body().contains(">0<"));
    }

    @Test
    void acaoIncrementarReidrataEstadoEDevolveFragmento() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.Contador");

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=incrementar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
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

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=hashCode"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
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

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=alternar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
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

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=alternar"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
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
     */
    @Test
    void excecaoInesperadaDeUmaAcaoVira500EmVezDeDerrubarAConexao() throws Exception {
        String selector = SelectorDerivation.derive("dev.jta.standalone.ComponenteInstavel");

        HttpResponse<String> response = client.send(
                HttpRequest.newBuilder(URI.create(baseUrl() + "/__jta/action/" + selector + "?action=explodir"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(""))
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        assertEquals(500, response.statusCode());
    }
}
