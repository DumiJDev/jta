package dev.jta.standalone;

import dev.jta.core.SelectorDerivation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

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

    @Test
    void endpointSseTransmiteHtmlRenderizadoParaClienteConectado() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/sse/placar")).GET().build();

        // JtaHttpServer mantem a HttpExchange aberta e escreve eventos de
        // SseHub (jta-runtime) diretamente no OutputStream - lemos so a
        // primeira linha "data:" para provar a inscricao/broadcast, sem
        // esperar o stream (infinito) terminar sozinho.
        CompletableFuture<Optional<String>> firstDataLine = CompletableFuture.supplyAsync(() -> {
            try {
                HttpResponse<Stream<String>> response = client.send(request, HttpResponse.BodyHandlers.ofLines());
                assertEquals(200, response.statusCode());
                assertEquals("text/event-stream; charset=utf-8", response.headers().firstValue("Content-Type").orElse(null));
                // try-with-resources: so consumir a primeira linha (findFirst)
                // sem fechar o Stream deixaria a conexao SSE pendurada.
                try (Stream<String> lines = response.body()) {
                    return lines.filter(line -> line.startsWith("data:")).findFirst();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Optional<String> dataLine = firstDataLine.get(5, TimeUnit.SECONDS);
        assertTrue(dataLine.isPresent());
        assertTrue(dataLine.get().contains(">7<"));
    }
}
