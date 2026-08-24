package dev.jta.demo.tutores;

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
 * Prova de ponta a ponta de path params, DI de um @Service Spring,
 * persistencia real (JPA + H2) com relacao um-para-muitos (Tutor -> Pet
 * -> Visita), CRUD completo e criacao aninhada. Substitui
 * ProdutoIntegrationTest na reforma do demo para o dominio de clinica
 * veterinaria - mesmas garantias, dominio novo.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TutorIntegrationTest {

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

    @Test
    void listaTutoresViaServicoInjetado() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/tutores", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Maria Silva", "Joao Pereira");
    }

    @Test
    void paginaDeDetalheReidrataIdDoPathParamEListaPets() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/tutores/1", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Maria Silva", "Rex");
    }

    @Test
    void idInexistenteMostraMensagemDeFallbackSemQuebrar() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/tutores/nao-existe", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("nao encontrado");
    }

    @Test
    void duasRequisicoesConcorrentesNaoVazamEstadoEntreSi() {
        // se TutorDetalhe estivesse registrado sem @Scope("prototype"), a
        // segunda chamada poderia devolver o tutor da primeira (mesma
        // instancia singleton reaproveitada) - trava exatamente essa regressao.
        ResponseEntity<String> tutor1 = rest.getForEntity(baseUrl() + "/tutores/1", String.class);
        ResponseEntity<String> tutor2 = rest.getForEntity(baseUrl() + "/tutores/2", String.class);

        assertThat(tutor1.getBody()).contains("Maria Silva").doesNotContain("Joao Pereira");
        assertThat(tutor2.getBody()).contains("Joao Pereira").doesNotContain("Maria Silva");
    }

    @Test
    void homeLigaParaTodosOsDemos() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/", String.class);
        assertThat(response.getBody()).contains("/contador", "/tutores", "/veterinarios", "/tarefas");
    }

    @Test
    void criarTutorComNomeVazioBloqueiaAAcao() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nome", "");
        form.add("telefone", "");

        ResponseEntity<String> response = postAction("dev.jta.demo.tutores.TutorNovo", "criar", form);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().get("HX-Redirect")).isNull();
        assertThat(response.getBody()).contains("Nome e obrigatorio");
    }

    @Test
    void criarTutorComDadosValidosPersisteERedirecionaParaODetalhe() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nome", "Beatriz Lima");
        form.add("telefone", "(11) 90000-0000");
        form.add("endereco", "Rua Nova, 789");

        ResponseEntity<String> response = postAction("dev.jta.demo.tutores.TutorNovo", "criar", form);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String redirectTo = response.getHeaders().getFirst("HX-Redirect");
        assertThat(redirectTo).isNotNull().startsWith("/tutores/");

        ResponseEntity<String> lista = rest.getForEntity(baseUrl() + "/tutores", String.class);
        assertThat(lista.getBody()).contains("Beatriz Lima");
    }

    @Test
    void editarTutorPreCarregaDadosExistentesEAtualizaAoSalvar() {
        ResponseEntity<String> formulario = rest.getForEntity(baseUrl() + "/tutores/2/editar", String.class);
        assertThat(formulario.getBody()).contains("value=\"Joao Pereira\"");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", "2");
        form.add("nome", "Joao Pereira Jr.");
        form.add("telefone", "(11) 98888-1122");
        form.add("endereco", "Av. Central, 456");

        ResponseEntity<String> response = postAction("dev.jta.demo.tutores.TutorEditar", "salvar", form);
        assertThat(response.getHeaders().getFirst("HX-Redirect")).isEqualTo("/tutores/2");

        ResponseEntity<String> detalhe = rest.getForEntity(baseUrl() + "/tutores/2", String.class);
        assertThat(detalhe.getBody()).contains("Joao Pereira Jr.");
    }

    @Test
    void criarPetAninhadoSobTutorEDepoisRegistrarVisitaAdicionaAoHistorico() {
        MultiValueMap<String, String> criarPetForm = new LinkedMultiValueMap<>();
        criarPetForm.add("tutorId", "1");
        criarPetForm.add("nome", "Bolinha");
        criarPetForm.add("especie", "Coelho");

        ResponseEntity<String> criado = postAction("dev.jta.demo.pets.PetNovo", "criar", criarPetForm);
        String petPath = criado.getHeaders().getFirst("HX-Redirect");
        assertThat(petPath).isNotNull().startsWith("/pets/");

        // apareceu no tutor certo
        ResponseEntity<String> tutorDetalhe = rest.getForEntity(baseUrl() + "/tutores/1", String.class);
        assertThat(tutorDetalhe.getBody()).contains("Bolinha", "Coelho");

        String petId = petPath.substring(petPath.lastIndexOf('/') + 1);
        MultiValueMap<String, String> visitaForm = new LinkedMultiValueMap<>();
        visitaForm.add("id", petId);
        visitaForm.add("dataVisita", "2026-08-01");
        visitaForm.add("descricaoVisita", "Primeira consulta");

        ResponseEntity<String> comVisita = postAction("dev.jta.demo.pets.PetDetalhe", "registrarVisita", visitaForm);
        assertThat(comVisita.getBody()).contains("Primeira consulta");
        // formulario de nova visita volta vazio apos sucesso
        assertThat(comVisita.getBody()).doesNotContain("value=\"Primeira consulta\"");
    }

    @Test
    void excluirTutorRemoveDoCatalogoERedirecionaEExcluiPetsEmCascata() {
        MultiValueMap<String, String> criarForm = new LinkedMultiValueMap<>();
        criarForm.add("nome", "Tutor descartavel");
        criarForm.add("telefone", "0000-0000");
        ResponseEntity<String> criado = postAction("dev.jta.demo.tutores.TutorNovo", "criar", criarForm);
        String pathCriado = criado.getHeaders().getFirst("HX-Redirect");
        String idCriado = pathCriado.substring(pathCriado.lastIndexOf('/') + 1);

        MultiValueMap<String, String> excluirForm = new LinkedMultiValueMap<>();
        excluirForm.add("id", idCriado);
        ResponseEntity<String> resposta = postAction("dev.jta.demo.tutores.TutorDetalhe", "excluir", excluirForm);

        assertThat(resposta.getHeaders().getFirst("HX-Redirect")).isEqualTo("/tutores");

        ResponseEntity<String> lista = rest.getForEntity(baseUrl() + "/tutores", String.class);
        assertThat(lista.getBody()).doesNotContain("Tutor descartavel");
    }
}
