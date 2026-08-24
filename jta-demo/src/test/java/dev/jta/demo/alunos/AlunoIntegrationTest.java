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

    private ResponseEntity<String> postAction(String fqn, String action, MultiValueMap<String, String> form) {
        String selector = SelectorDerivation.derive(fqn);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
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
