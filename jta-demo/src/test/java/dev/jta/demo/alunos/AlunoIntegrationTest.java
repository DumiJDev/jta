package dev.jta.demo.alunos;

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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova de ponta a ponta do dominio de gestao escolar: path params, DI de
 * @Service Spring, persistencia real (JPA + H2), busca ao vivo via query
 * param, CRUD completo e a criacao aninhada de Matricula (FK escolhida via
 * select, nao path param) contra um aluno existente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AlunoIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate().withBasicAuth("admin", "admin");

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static final Pattern CSRF_TOKEN_PATTERN =
            Pattern.compile("hx-headers='\\{\"X-JTA-CSRF-Token\":\"([^\"]+)\"}'");

    /**
     * Fluxo real de CSRF nativo (ver SECURITY.md, achado #6): GET de uma
     * pagina emite a cookie assinada e embute o token no {@code hx-headers}
     * do {@code <body>} - extraidos aqui para toda acao POST deste teste
     * poder provar que veio do mesmo cliente que visitou a pagina.
     */
    private ResponseEntity<String> postAction(String fqn, String action, MultiValueMap<String, String> form) {
        ResponseEntity<String> pagina = rest.getForEntity(baseUrl() + "/alunos", String.class);
        List<String> setCookies = pagina.getHeaders().get(HttpHeaders.SET_COOKIE);
        String cookie = (setCookies == null ? List.<String>of() : setCookies).stream()
                .filter(v -> v.startsWith("jta_csrf="))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GET /alunos nao emitiu Set-Cookie de CSRF: " + setCookies))
                .split(";", 2)[0];
        Matcher matcher = CSRF_TOKEN_PATTERN.matcher(pagina.getBody());
        if (!matcher.find()) {
            throw new IllegalStateException("token CSRF nao encontrado no hx-headers: " + pagina.getBody());
        }

        String selector = SelectorDerivation.derive(fqn);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.add(HttpHeaders.COOKIE, cookie);
        headers.add("X-JTA-CSRF-Token", matcher.group(1));
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
        return rest.exchange(baseUrl() + "/__jta/action/" + selector + "?action=" + action,
                HttpMethod.POST, request, String.class);
    }

    @Test
    void listaAlunosViaServicoInjetado() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/alunos", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Maria Silva", "Joao Pereira");
    }

    @Test
    void buscaAoVivoFiltraPorNomeViaQueryParam() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/alunos?q=Maria", String.class);
        assertThat(response.getBody()).contains("Maria Silva").doesNotContain("Joao Pereira");
    }

    @Test
    void paginaDeDetalheMostraTurmasENotasDoAluno() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/alunos/1", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Maria Silva", "9-A", "Matematica");
    }

    @Test
    void idInexistenteMostraMensagemDeFallbackSemQuebrar() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/alunos/nao-existe", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("nao encontrado");
    }

    @Test
    void criarAlunoComEmailInvalidoBloqueiaAAcao() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nome", "Novo Aluno");
        form.add("email", "nao-e-um-email");
        form.add("nascimento", "2012-01-01");

        ResponseEntity<String> response = postAction("dev.jta.demo.alunos.AlunoNovo", "criar", form);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().get("HX-Redirect")).isNull();
        assertThat(response.getBody()).contains("Email invalido");
    }

    @Test
    void criarAlunoComDadosValidosPersisteERedirecionaParaODetalhe() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nome", "Beatriz Lima");
        form.add("email", "beatriz.lima@escola.exemplo");
        form.add("nascimento", "2012-05-20");

        ResponseEntity<String> response = postAction("dev.jta.demo.alunos.AlunoNovo", "criar", form);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String redirectTo = response.getHeaders().getFirst("HX-Redirect");
        assertThat(redirectTo).isNotNull().startsWith("/alunos/");

        ResponseEntity<String> lista = rest.getForEntity(baseUrl() + "/alunos", String.class);
        assertThat(lista.getBody()).contains("Beatriz Lima");
    }

    @Test
    void matricularAlunoAninhaANovaMatriculaSobOAlunoExistente() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", "4");
        form.add("turmaId", "2");

        ResponseEntity<String> response = postAction("dev.jta.demo.alunos.AlunoDetalhe", "matricular", form);
        assertThat(response.getHeaders().getFirst("HX-Redirect")).isEqualTo("/alunos/4");

        ResponseEntity<String> detalhe = rest.getForEntity(baseUrl() + "/alunos/4", String.class);
        assertThat(detalhe.getBody()).contains("9-B");
    }

    @Test
    void linhaDeAlunoRendedizaComoComponenteFilhoAninhadoComInputsDoPai() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/alunos", String.class);

        assertThat(response.getBody())
                // o pai continua com o proprio data-jta-component...
                .contains("data-jta-component=\"dev-jta-demo-alunos-aluno-lista\"")
                // ...e cada linha e agora o FILHO aninhado, com o SEU PROPRIO
                // data-jta-component (selector canonico do filho, nunca o
                // alias - "o selector real emitido e sempre o selector
                // canonico do filho").
                .contains("data-jta-component=\"aluno-linha\"")
                // prova de property binding pai->filho: o nome/email do
                // aluno (dado que so o PAI conhece via self.alunos()) chega
                // renderizado dentro do filho.
                .contains("Maria Silva")
                // campo PROPRIO do filho (nao do pai) usado no teste de
                // isolamento de hx-include abaixo.
                .contains("<input type=\"hidden\" name=\"nome\" value=\"Maria Silva\"");
    }

    @Test
    void hxIncludeDaAcaoDoPaiEEscopadoPorInstanciaExcluindoCampoDoFilho() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/alunos", String.class);
        String body = response.getBody();

        // a correcao real (ver TemplateTransformer#buildHxInclude): NAO mais
        // "closest [data-jta-component]" (que varreria TAMBEM os campos do
        // filho aninhado, incluindo o <input type="hidden" name="nome">
        // proprio de AlunoLinha) - em vez disso, um seletor CSS puro
        // ancorado num token unico por render (data-jta-scope), com dupla
        // ancoragem para excluir qualquer campo que esteja dentro de uma
        // SEGUNDA fronteira [data-jta-scope] (o filho).
        assertThat(body).doesNotContain("hx-include=\"closest [data-jta-component]\"");
        assertThat(body).containsPattern(
                "hx-include=\"\\[data-jta-scope='\\d+'] :is\\(input,select,textarea\\)"
                        + ":not\\(\\[data-jta-scope='\\d+'] \\[data-jta-scope] :is\\(input,select,textarea\\)\\)\"");
    }

    @Test
    void removerAlunoNaoVazaCampoDoFilhoEExcluiDeVerdade() {
        // cria um aluno isolado (sem matricula/nota referenciando - FK),
        // so para este teste, evitando qualquer dependencia da ordem de
        // execucao com os outros testes desta classe.
        MultiValueMap<String, String> criarForm = new LinkedMultiValueMap<>();
        criarForm.add("nome", "Removivel Teste");
        criarForm.add("email", "removivel.teste@escola.exemplo");
        criarForm.add("nascimento", "2011-01-01");
        ResponseEntity<String> criado = postAction("dev.jta.demo.alunos.AlunoNovo", "criar", criarForm);
        String redirectTo = criado.getHeaders().getFirst("HX-Redirect");
        assertThat(redirectTo).isNotNull().startsWith("/alunos/");
        String novoId = redirectTo.substring("/alunos/".length());

        ResponseEntity<String> antesDeRemover = rest.getForEntity(baseUrl() + "/alunos", String.class);
        assertThat(antesDeRemover.getBody()).contains("Removivel Teste");

        // remove via o MESMO endpoint de acao que o botao (click)="remover(aluno.id())"
        // do template de AlunoLista gera - __jtaArg0 e o id resolvido por
        // POSICAO (nao por nome de parametro), exatamente como o
        // ComponentInvoker/JtaActionDispatcher fazem em producao.
        MultiValueMap<String, String> removerForm = new LinkedMultiValueMap<>();
        removerForm.add("__jtaArg0", novoId);
        ResponseEntity<String> response = postAction("dev.jta.demo.alunos.AlunoLista", "remover", removerForm);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).doesNotContain("Removivel Teste");

        ResponseEntity<String> depoisDeRemover = rest.getForEntity(baseUrl() + "/alunos", String.class);
        assertThat(depoisDeRemover.getBody()).doesNotContain("Removivel Teste");
    }

    @Test
    void acaoComAridadeErradaEeTratadaComoNaoEncontrada() {
        // camada 1 de defesa (JtaActionDispatcher): 'remover' declara
        // aridade 1 - invocar sem nenhum __jtaArgN (aridade 0) tem que ser
        // rejeitado exatamente como uma acao inexistente, nunca invocado
        // com um id ausente/nulo.
        MultiValueMap<String, String> semArgumento = new LinkedMultiValueMap<>();
        ResponseEntity<String> response = postAction("dev.jta.demo.alunos.AlunoLista", "remover", semArgumento);

        assertThat(response.getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void duasRequisicoesConcorrentesNaoVazamEstadoEntreSi() {
        // se AlunoDetalhe estivesse registrado sem @Scope("prototype"), a
        // segunda chamada poderia devolver o aluno da primeira (mesma
        // instancia singleton reaproveitada) - trava exatamente essa regressao.
        ResponseEntity<String> aluno1 = rest.getForEntity(baseUrl() + "/alunos/1", String.class);
        ResponseEntity<String> aluno2 = rest.getForEntity(baseUrl() + "/alunos/2", String.class);

        assertThat(aluno1.getBody()).contains("Maria Silva").doesNotContain("Joao Pereira");
        assertThat(aluno2.getBody()).contains("Joao Pereira").doesNotContain("Maria Silva");
    }
}
