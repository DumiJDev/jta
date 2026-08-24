package dev.jta.demo;

import dev.jta.core.SelectorDerivation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste de ponta a ponta contra a aplicacao real (servidor embutido, porta
 * aleatoria) - existe especificamente para nao repetir a historia recente
 * do projeto: os ultimos 6 bugs corrigidos (manifest ausente, TemplateEngine
 * on-demand vs precompilado, HTMX nunca importado, CSS nunca emitido,
 * @PathVariable sem -parameters) so foram descobertos rodando manualmente
 * na maquina do dev. Cada assert abaixo corresponde a um desses bugs -
 * se algum regredir, "mvn verify" falha antes de chegar em producao.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContadorIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void paginaContadorCarregaHtmxEAplicaCss() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/contador", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String body = response.getBody();
        assertThat(body).isNotNull();

        // bug #4: HTMX nunca era importado - sem isso, os hx-* ficam inertes
        assertThat(body).contains("htmx.org");

        // bug #5: CSS de @AComponent(styleUrl=...) nunca era emitido.
        // Contador usa styleUrl() externo (Contador.css), que declara
        // apenas a regra #valor - nao h1 (a asserção antiga checava um
        // seletor que nunca existiu neste CSS desde a migração para
        // styleUrl() externo, e por isso quebrava o "mvn verify" real).
        String selector = SelectorDerivation.derive("dev.jta.demo.Contador");
        assertThat(body).contains("[data-jta-component=\"" + selector + "\"] #valor");

        // o componente em si precisa estar la, com o estado inicial
        assertThat(body).contains("data-jta-component=\"" + selector + "\"");
        assertThat(body).contains(">0<"); // valor inicial
    }

    @Test
    void acaoIncrementarAtualizaEstadoEDevolveFragmentoSemShell() {
        String selector = SelectorDerivation.derive("dev.jta.demo.Contador");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("valor", "5");
        form.add("titulo", "Cliques");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        // bug #6: @PathVariable sem nome explicito quebrava aqui com
        // "parameter name information not available via reflection"
        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/__jta/action/" + selector + "?action=incrementar",
                HttpMethod.POST, request, String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String body = response.getBody();
        assertThat(body).isNotNull();

        // reidratou valor=5 do form e incrementou para 6
        assertThat(body).contains(">6<");

        // fragmento de acao NAO deve ter o shell de pagina completo (isso
        // vai para o hx-swap="outerHTML" do elemento, nao a pagina toda)
        assertThat(body).doesNotContain("<!DOCTYPE html>");
    }

    @Test
    void acaoIncrementarVariasVezesMostraMensagemDeLimite() {
        String selector = SelectorDerivation.derive("dev.jta.demo.Contador");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("valor", "11");
        form.add("titulo", "Cliques");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        ResponseEntity<String> response = rest.exchange(
                baseUrl() + "/__jta/action/" + selector + "?action=incrementar",
                HttpMethod.POST, request, String.class);

        assertThat(response.getBody()).contains("Muitos!");
    }
}
