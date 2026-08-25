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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trava contra regressao os dois achados mais graves de SECURITY.md, mais
 * as duas roles distintas (ADMIN/PROFESSOR) do dominio de gestao escolar -
 * {@code @RequiresRole} nao e so "logado ou nao".
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityRegressionTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static final Pattern CSRF_TOKEN_PATTERN =
            Pattern.compile("hx-headers='\\{\"X-JTA-CSRF-Token\":\"([^\"]+)\"}'");

    private record Csrf(String cookie, String headerValue) {
    }

    /**
     * Fluxo real de CSRF nativo (ver SECURITY.md, achado #6): GET de uma
     * pagina publica (catalogo de turmas, sem autenticacao) emite a cookie
     * assinada e embute o token no {@code hx-headers} do {@code <body>} -
     * o token nao esta amarrado a nenhum componente/pagina especifica,
     * entao serve para autorizar POSTs a qualquer acao, contanto que
     * venham do MESMO cliente (mesma cookie).
     */
    private Csrf fetchCsrf(TestRestTemplate client) {
        ResponseEntity<String> response = client.getForEntity(baseUrl() + "/turmas", String.class);
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        String setCookie = (setCookies == null ? List.<String>of() : setCookies).stream()
                .filter(v -> v.startsWith("jta_csrf="))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GET /turmas nao emitiu Set-Cookie de CSRF: " + setCookies));
        String cookie = setCookie.split(";", 2)[0];
        Matcher matcher = CSRF_TOKEN_PATTERN.matcher(response.getBody());
        if (!matcher.find()) {
            throw new IllegalStateException("token CSRF nao encontrado no hx-headers: " + response.getBody());
        }
        return new Csrf(cookie, matcher.group(1));
    }

    /** POST de acao com o fluxo de CSRF valido (cookie + header) - o caminho feliz usado pela maioria dos testes. */
    private ResponseEntity<String> postAction(TestRestTemplate client, String fqn, String action,
                                               MultiValueMap<String, String> form) {
        Csrf csrf = fetchCsrf(client);
        return postActionRaw(client, fqn, action, form, csrf.cookie(), csrf.headerValue());
    }

    /** POST de acao com controle total sobre cookie/header de CSRF (ou ausencia deles) - para os testes de regressao de CSRF. */
    private ResponseEntity<String> postActionRaw(TestRestTemplate client, String fqn, String action,
                                                  MultiValueMap<String, String> form, String cookie, String csrfHeaderValue) {
        String selector = SelectorDerivation.derive(fqn);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        if (cookie != null) {
            headers.add(HttpHeaders.COOKIE, cookie);
        }
        if (csrfHeaderValue != null) {
            headers.add("X-JTA-CSRF-Token", csrfHeaderValue);
        }
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        return client.exchange(baseUrl() + "/__jta/action/" + selector + "?action=" + action,
                HttpMethod.POST, request, String.class);
    }

    // --- SECURITY.md achado #6 (CSRF nativo) ---

    @Test
    void getDePaginaEmiteCookieDeCsrfEHtmlContemHxHeadersComOMesmoToken() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/turmas", String.class);
        List<String> setCookies = response.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        assertThat(setCookies).anyMatch(v -> v.startsWith("jta_csrf="));
        Matcher matcher = CSRF_TOKEN_PATTERN.matcher(response.getBody());
        assertThat(matcher.find()).isTrue();
    }

    @Test
    void postSemCookieNemHeaderCsrfENegado() {
        ResponseEntity<String> response = postActionRaw(rest, "dev.jta.demo.SecurityProbe", "tocar",
                new LinkedMultiValueMap<>(), null, null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void postComCookieMasSemHeaderCsrfENegado() {
        Csrf csrf = fetchCsrf(rest);
        ResponseEntity<String> response = postActionRaw(rest, "dev.jta.demo.SecurityProbe", "tocar",
                new LinkedMultiValueMap<>(), csrf.cookie(), null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void postComHeaderMasSemCookieCsrfENegado() {
        Csrf csrf = fetchCsrf(rest);
        ResponseEntity<String> response = postActionRaw(rest, "dev.jta.demo.SecurityProbe", "tocar",
                new LinkedMultiValueMap<>(), null, csrf.headerValue());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void postComCookieForjadaComOutroSegredoENegado() {
        // simula um token "de outra origem": mesmo formato (token.assinatura),
        // mas a assinatura nunca bateria com HMAC-SHA256(segredo-real, token) -
        // o cenario que o double-submit assinado existe para barrar.
        Csrf csrf = fetchCsrf(rest);
        String forgedCookie = "jta_csrf=" + csrf.headerValue() + ".assinatura-forjada-de-outra-origem";
        ResponseEntity<String> response = postActionRaw(rest, "dev.jta.demo.SecurityProbe", "tocar",
                new LinkedMultiValueMap<>(), forgedCookie, csrf.headerValue());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void fluxoFelizGetDepoisPostComCookieETokenExtraidosDoHtmlFunciona() {
        ResponseEntity<String> response = postAction(rest, "dev.jta.demo.SecurityProbe", "tocar", new LinkedMultiValueMap<>());
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    // SECURITY.md achado #1 (CRITICO): so metodos void que o processor
    // registrou em ComponentMetadata.actions() podem ser invocados via
    // ?action= - nao qualquer metodo publico sem argumentos da classe.
    @Test
    void actionParaMetodoDeTemplateNaoEExecutavel() {
        // isAdminComoTexto() existe e e publico, mas retorna String (nao e
        // void) - e um metodo de template, nunca uma acao.
        ResponseEntity<String> response =
                postAction(rest, "dev.jta.demo.SecurityProbe", "isAdminComoTexto", new LinkedMultiValueMap<>());
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void actionParaMetodoHerdadoDeObjectNaoEExecutavel() {
        // hashCode() e publico e sem argumentos, mas herdado de Object -
        // nunca foi declarado pelo dev nem existe em
        // ComponentMetadata.actions() (o processor so enxerga membros
        // declarados diretamente na classe).
        ResponseEntity<String> response =
                postAction(rest, "dev.jta.demo.SecurityProbe", "hashCode", new LinkedMultiValueMap<>());
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

        ResponseEntity<String> response = postAction(rest, "dev.jta.demo.SecurityProbe", "tocar", form);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("sonda"); // 'nome' e bindavel normalmente
        assertThat(response.getBody()).contains(">false<"); // 'isAdmin' NAO foi setado, apesar de isAdmin=true no form
    }

    // OWASP A03:2021 (Injection/XSS): confirma que o escaping automatico do
    // JTE (ContentType.Html) se aplica tanto em contexto de texto
    // ({{ nome() }} dentro de <h1>, em AlunoDetalhe) quanto em contexto de
    // atributo (value="{{ nome }}", em AlunoEditar).
    @Test
    void nomeDeAlunoComPayloadDeXssEEscapadoTantoEmTextoQuantoEmAtributo() {
        String payload = "<script>alert(1)</script>\"><svg onload=alert(2)>";
        TestRestTemplate admin = rest.withBasicAuth("admin", "admin");

        MultiValueMap<String, String> criarForm = new LinkedMultiValueMap<>();
        criarForm.add("nome", payload);
        criarForm.add("email", "xss@escola.exemplo");
        criarForm.add("nascimento", "2012-01-01");
        ResponseEntity<String> criado = postAction(admin, "dev.jta.demo.alunos.AlunoNovo", "criar", criarForm);
        String detalhePath = criado.getHeaders().getFirst("HX-Redirect");
        assertThat(detalhePath).isNotNull();

        // contexto de texto: {{ nome() }} dentro de <h1> em AlunoDetalhe
        ResponseEntity<String> detalhe = admin.getForEntity(baseUrl() + detalhePath, String.class);
        assertThat(detalhe.getBody()).doesNotContain("<script>alert(1)</script>");
        assertThat(detalhe.getBody()).contains("&lt;script&gt;");

        // contexto de atributo: value="{{ nome }}" em AlunoEditar, carregado do banco via init()
        String id = detalhePath.substring(detalhePath.lastIndexOf('/') + 1);
        ResponseEntity<String> editar = admin.getForEntity(baseUrl() + "/alunos/" + id + "/editar", String.class);
        assertThat(editar.getBody()).doesNotContain("\"><svg onload=alert(2)>");
    }

    // As tres respostas possiveis de SecurityEnforcer.isAuthorized: sem
    // autenticacao, autenticado com role diferente da exigida, autenticado
    // com a role certa - agora com DUAS roles distintas (ADMIN/PROFESSOR),
    // nao so "logado ou nao".
    @Test
    void cadastroDeAlunoSemLoginENegado() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/alunos/novo", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cadastroDeAlunoComRoleProfessorENegado() {
        // PROFESSOR e uma role valida no sistema, so nao para esta pagina -
        // prova que @RequiresRole("ADMIN") checa a role especifica, nao so "esta logado".
        ResponseEntity<String> response = rest.withBasicAuth("professor", "professor")
                .getForEntity(baseUrl() + "/alunos/novo", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cadastroDeAlunoComRoleAdminEPermitido() {
        ResponseEntity<String> response = rest.withBasicAuth("admin", "admin")
                .getForEntity(baseUrl() + "/alunos/novo", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Novo aluno");
    }

    @Test
    void lancarNotaComRoleAdminENegadoMasComRoleProfessorEPermitido() {
        // inverso do teste anterior: uma pagina de PROFESSOR nega ADMIN.
        ResponseEntity<String> comoAdmin = rest.withBasicAuth("admin", "admin")
                .getForEntity(baseUrl() + "/notas/lancar/1", String.class);
        assertThat(comoAdmin.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> comoProfessor = rest.withBasicAuth("professor", "professor")
                .getForEntity(baseUrl() + "/notas/lancar/1", String.class);
        assertThat(comoProfessor.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(comoProfessor.getBody()).contains("Lancar nota");
    }

    @Test
    void catalogoPublicoDeTurmasEDisciplinasNaoExigeLogin() {
        assertThat(rest.getForEntity(baseUrl() + "/turmas", String.class).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rest.getForEntity(baseUrl() + "/disciplinas", String.class).getStatusCode().is2xxSuccessful()).isTrue();
    }
}
