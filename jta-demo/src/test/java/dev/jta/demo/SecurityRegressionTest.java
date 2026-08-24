package dev.jta.demo;

import dev.jta.core.SelectorDerivation;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava contra regressao os dois achados mais graves de SECURITY.md.
 * Antes desta suite, o achado #1 (critico) so tinha sido verificado por
 * leitura de codigo - nunca por uma requisicao HTTP real, ja que o
 * ambiente original de desenvolvimento nao conseguia compilar
 * jta-spring-boot-starter/jta-demo (sem acesso ao Maven Central). O
 * achado #5 so tinha a metade de compile-time testada
 * (ver scripts/smoke-test.sh); a aplicacao em runtime
 * (JtaComponentInvoker.populateFromParams) nunca tinha sido exercitada
 * contra o endpoint de acao de verdade.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityRegressionTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private ResponseEntity<String> postAction(String fqn, String action, MultiValueMap<String, String> form) {
        String selector = SelectorDerivation.derive(fqn);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        return rest.exchange(baseUrl() + "/__jta/action/" + selector + "?action=" + action,
                HttpMethod.POST, request, String.class);
    }

    // SECURITY.md achado #1 (CRITICO): so metodos void que o processor
    // registrou em ComponentMetadata.actions() podem ser invocados via
    // ?action= - nao qualquer metodo publico sem argumentos da classe.
    @Test
    void actionParaMetodoDeTemplateNaoEExecutavel() {
        // mensagem() existe e e publico, mas retorna String (nao e void) -
        // e um metodo de template, nunca uma acao.
        ResponseEntity<String> response = postAction("dev.jta.demo.Contador", "mensagem", new LinkedMultiValueMap<>());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void actionParaMetodoHerdadoDeObjectNaoEExecutavel() {
        // hashCode() e publico e sem argumentos, mas herdado de Object -
        // nunca foi declarado pelo dev nem existe em
        // ComponentMetadata.actions() (o processor so enxerga membros
        // declarados diretamente na classe). Antes da correcao do achado
        // #1, isto (e ate wait()) era invocavel via reflection.
        ResponseEntity<String> response = postAction("dev.jta.demo.Contador", "hashCode", new LinkedMultiValueMap<>());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // SECURITY.md achado #5 (MEDIO): so campos que o template referencia
    // diretamente via {{ }} (mais @Bindable/path params) sao populados a
    // partir da requisicao - um campo publico "por acaso" nao vira
    // mass-assignable so por ser publico.
    @Test
    void campoNuncaInterpoladoDiretamenteNaoEBindavelViaRequisicao() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nome", "sonda");
        form.add("isAdmin", "true"); // tentativa de mass assignment

        ResponseEntity<String> response = postAction("dev.jta.demo.SecurityProbe", "tocar", form);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("sonda"); // 'nome' e bindavel normalmente
        assertThat(response.getBody()).contains(">false<"); // 'isAdmin' NAO foi setado, apesar de isAdmin=true no form
    }

    // OWASP A03:2021 (Injection/XSS): confirma que o escaping automatico do
    // JTE (ContentType.Html) realmente se aplica tanto em contexto de texto
    // ({{ nome() }} dentro de <h1>, em TutorDetalhe) quanto em contexto de
    // atributo (value="{{ nome }}", em TutorEditar) - nao era um fato
    // documentado em SECURITY.md nem verificado por nenhum teste ate agora,
    // so assumido como comportamento padrao do JTE. Usa Tutor (nao Produto -
    // o demo foi reformado para o dominio de clinica veterinaria) so pela
    // mesma forma de template (interpolacao de texto + atributo), sem
    // relacao com o achado em si.
    @Test
    void nomeDeTutorComPayloadDeXssEEscapadoTantoEmTextoQuantoEmAtributo() {
        String payload = "<script>alert(1)</script>\"><svg onload=alert(2)>";

        MultiValueMap<String, String> criarForm = new LinkedMultiValueMap<>();
        criarForm.add("nome", payload);
        criarForm.add("telefone", "0000-0000");
        ResponseEntity<String> criado = postAction("dev.jta.demo.tutores.TutorNovo", "criar", criarForm);
        String detalhePath = criado.getHeaders().getFirst("HX-Redirect");
        assertThat(detalhePath).isNotNull();

        // contexto de texto: {{ nome() }} dentro de <h1> em TutorDetalhe
        ResponseEntity<String> detalhe = rest.getForEntity(baseUrl() + detalhePath, String.class);
        assertThat(detalhe.getBody()).doesNotContain("<script>alert(1)</script>");
        assertThat(detalhe.getBody()).contains("&lt;script&gt;");

        // contexto de atributo: value="{{ nome }}" em TutorEditar, carregado do banco via init()
        String id = detalhePath.substring(detalhePath.lastIndexOf('/') + 1);
        ResponseEntity<String> editar = rest.getForEntity(baseUrl() + "/tutores/" + id + "/editar", String.class);
        assertThat(editar.getBody()).doesNotContain("\"><svg onload=alert(2)>");
    }
}
