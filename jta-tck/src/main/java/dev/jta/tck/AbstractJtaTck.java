package dev.jta.tck;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.function.Executable;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * TCK (Technology Compatibility Kit): a mesma bateria de testes de
 * contrato HTTP, rodada contra os 4 adaptadores JTA. Cada adaptador
 * estende esta classe e implementa {@link #createHarness()} apontando
 * para fixtures/servidor proprios (ver subclasses em
 * jta-javalin-starter/jta-standalone/jta-spring-boot-starter/
 * jta-quarkus-extension).
 *
 * <p><b>Skip nomeado, nunca "passa" silencioso:</b> onde
 * {@link JtaAdapterHarness#supportedFeatures()} nao inclui a feature de um
 * teste, o teste nao roda o corpo - vira um "aborted" do JUnit 5
 * ({@link org.junit.jupiter.api.Assumptions#assumeTrue}) com o motivo
 * exato de {@link JtaAdapterHarness#skipReason}, visivel no relatorio do
 * Surefire como skip nomeado (nao como falha, nem como sucesso nao
 * verificado). E exatamente o mecanismo que o plano-mestre pedia:
 * "transforma cada lacuna hoje silenciosa num skip nomeado e visivel no
 * relatorio".
 *
 * <p>Ao final da classe, {@link #writeReport()} grava
 * {@code target/jta-tck-report.properties} do modulo do adaptador -
 * {@link CompatibilityMatrixGenerator} agrega esses arquivos de todos os
 * modulos numa matriz Markdown feature x adaptador.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractJtaTck {

    private static final HttpClient CLIENT = HttpClient.newHttpClient();

    private final Map<JtaFeature, TckResult> results = new EnumMap<>(JtaFeature.class);
    private JtaAdapterHarness harness;

    /** Implementado por cada adaptador - devolve um harness ja pronto para {@link JtaAdapterHarness#start()}. */
    protected abstract JtaAdapterHarness createHarness();

    @BeforeAll
    void startHarness() throws Exception {
        harness = createHarness();
        harness.start();
    }

    @AfterAll
    void stopHarnessAndWriteReport() throws Exception {
        try {
            harness.stop();
        } finally {
            writeReport();
        }
    }

    private void writeReport() {
        TckReportWriter.write(harness.adapterName(), results, Path.of(System.getProperty("user.dir"), "target"));
    }

    /**
     * Registra o resultado da feature e, se nao suportada, aborta o teste
     * (skip nomeado) antes de rodar {@code body}. Se suportada, roda
     * {@code body}: sucesso vira {@link TckStatus#SUPORTADO}, excecao vira
     * {@link TckStatus#FALHOU} (e a excecao original e relancada, para o
     * Surefire tambem reportar a falha normalmente).
     */
    private void verify(JtaFeature feature, Executable body) throws Throwable {
        if (!harness.supportedFeatures().contains(feature)) {
            String reason = harness.skipReason(feature);
            results.put(feature, TckResult.skipped(reason));
            assumeTrue(false, reason);
            return;
        }
        try {
            body.execute();
            results.put(feature, TckResult.supported());
        } catch (Throwable t) {
            results.put(feature, TckResult.failed(String.valueOf(t.getMessage())));
            throw t;
        }
    }

    @Test
    void routing() throws Throwable {
        verify(JtaFeature.ROUTING, () -> {
            HttpResponse<String> response = get(harness.routingProbe());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(harness.routingExpectedMarker()),
                    () -> "esperava conter '" + harness.routingExpectedMarker() + "', corpo: " + response.body());
        });
    }

    @Test
    void actions() throws Throwable {
        verify(JtaFeature.ACTIONS, () -> {
            HttpResponse<String> response = post(harness.actionProbe());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(harness.actionExpectedMarker()),
                    () -> "esperava conter '" + harness.actionExpectedMarker() + "', corpo: " + response.body());
        });
    }

    @Test
    void actionAllowlist() throws Throwable {
        verify(JtaFeature.ACTION_ALLOWLIST, () -> {
            // com CSRF valido de proposito: sem ele o pedido pararia no 403
            // do CSRF e o teste nao provaria nada sobre o allowlist, que e
            // justamente a feature medida aqui.
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(harness.actionAllowlistUrl()))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(""));
            applyCsrf(builder);

            HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(404, response.statusCode(),
                    "uma acao nao declarada (metodo real, mas fora do allowlist) devia devolver 404, nao ser invocada");
        });
    }

    @Test
    void roleAuthorization() throws Throwable {
        verify(JtaFeature.ROLE_AUTHORIZATION, () -> {
            HttpResponse<String> response = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(harness.roleProtectedUrl())).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, response.statusCode(),
                    "pagina @RequiresRole sem nenhuma autenticacao devia devolver 403");
        });
    }

    @Test
    void sse() throws Throwable {
        verify(JtaFeature.SSE, () -> {
            HttpProbe probe = harness.sseProbe();
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(probe.url())).GET();
            probe.headers().forEach(builder::header);

            HttpResponse<Stream<String>> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
            assertEquals(200, response.statusCode());

            Optional<String> dataLine;
            // try-with-resources: uma conexao SSE fica aberta indefinidamente
            // do lado do servidor ate o cliente fechar - so consumir a
            // primeira linha (findFirst) sem fechar o Stream deixa a conexao
            // HTTP subjacente pendurada, o que Tomcat (adaptador Spring) trata
            // como "requisicao ativa" e faz o shutdown gracioso do servidor
            // travar no fim da suite.
            try (Stream<String> lines = response.body()) {
                dataLine = lines.filter(line -> line.startsWith("data:")).findFirst();
            }
            assertTrue(dataLine.isPresent(), "esperava ao menos uma linha 'data:' do endpoint @Sse");
            assertTrue(dataLine.get().contains(harness.sseExpectedMarker()),
                    () -> "esperava conter '" + harness.sseExpectedMarker() + "', linha: " + dataLine.get());
        });
    }

    @Test
    void i18n() throws Throwable {
        verify(JtaFeature.I18N, () -> {
            HttpResponse<String> response = get(harness.i18nProbe());
            assertEquals(200, response.statusCode());
            assertTrue(response.body().contains(harness.i18nExpectedMarker()),
                    () -> "esperava conter '" + harness.i18nExpectedMarker() + "', corpo: " + response.body());
        });
    }

    @Test
    void csrf() throws Throwable {
        verify(JtaFeature.CSRF, () -> {
            throw new UnsupportedOperationException(
                    "CSRF ainda nao tem probe no TCK - nenhum adaptador declara suporte nesta versao");
        });
    }

    @Test
    void session() throws Throwable {
        verify(JtaFeature.SESSION, () -> {
            throw new UnsupportedOperationException(
                    "Sessao ainda nao tem probe no TCK - nenhum adaptador declara suporte nesta versao");
        });
    }

    @Test
    void fileUpload() throws Throwable {
        verify(JtaFeature.FILE_UPLOAD, () -> {
            throw new UnsupportedOperationException(
                    "Upload de arquivo ainda nao tem probe no TCK - nenhum adaptador declara suporte nesta versao");
        });
    }

    @Test
    void componentComposition() throws Throwable {
        verify(JtaFeature.COMPONENT_COMPOSITION, () -> {
            throw new UnsupportedOperationException(
                    "Composicao com @Input ainda nao tem probe no TCK - nenhum adaptador declara suporte nesta versao");
        });
    }

    private HttpResponse<String> get(HttpProbe probe) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(probe.url())).GET();
        probe.headers().forEach(builder::header);
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> post(ActionProbe probe) throws Exception {
        String body = probe.formParams().entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .reduce((a, b) -> a + "&" + b)
                .orElse("");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(probe.url()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        applyCsrf(builder);
        return CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static final Pattern CSRF_TOKEN_PATTERN =
            Pattern.compile("hx-headers='\\{\"([^\"]+)\":\"([^\"]+)\"}'");

    private record Csrf(String cookie, String headerName, String headerValue) {
    }

    /**
     * Faz o handshake de CSRF nativo (ver SECURITY.md, achado #6) antes de
     * um POST de acao: um GET de pagina emite a cookie assinada e embute o
     * token no {@code hx-headers} do {@code <body>}, e o POST precisa
     * devolver os dois (prova dupla cookie + header).
     *
     * <p>Deliberadamente tolerante: se o adaptador nao emitir cookie nem
     * token (CSRF desligado via {@code [security] csrf_mode = "disabled"},
     * ou adaptador que ainda nao implementa a feature), devolve
     * {@link Optional#empty()} e o POST segue sem os cabecalhos - o TCK
     * continua medindo a feature que o teste realmente cobre (acoes,
     * allowlist) em vez de falhar por causa de outra.
     */
    private Optional<Csrf> csrfHandshake() throws Exception {
        HttpResponse<String> page = get(harness.routingProbe());
        Matcher matcher = CSRF_TOKEN_PATTERN.matcher(page.body());
        if (!matcher.find()) {
            return Optional.empty();
        }
        String headerName = matcher.group(1);
        String headerValue = matcher.group(2);
        return page.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("jta_csrf="))
                .findFirst()
                .map(value -> new Csrf(value.split(";", 2)[0], headerName, headerValue));
    }

    private void applyCsrf(HttpRequest.Builder builder) throws Exception {
        Optional<Csrf> csrf = csrfHandshake();
        if (csrf.isPresent()) {
            builder.header("Cookie", csrf.get().cookie());
            builder.header(csrf.get().headerName(), csrf.get().headerValue());
        }
    }
}
