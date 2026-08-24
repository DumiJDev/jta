package dev.jta.demo.produtos;

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
 * persistencia real (JPA + H2), CRUD completo (criar/editar/excluir) e o
 * mecanismo de Redirect apos uma acao.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProdutoIntegrationTest {

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
    void catalogoListaProdutosViaServicoInjetado() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/produtos", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Teclado mecanico", "Mouse ergonomico", "Monitor 27");
    }

    @Test
    void paginaDeDetalheReidrataIdDoPathParam() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/produtos/1", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Teclado mecanico", "R$ 349.90");
    }

    @Test
    void idInexistenteMostraMensagemDeFallbackSemQuebrar() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/produtos/nao-existe", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("nao encontrado");
    }

    @Test
    void duasRequisicoesConcorrentesNaoVazamEstadoEntreSi() {
        // se ProdutoDetalhe estivesse registrado sem @Scope("prototype"),
        // a segunda chamada poderia devolver o produto da primeira (mesma
        // instancia singleton reaproveitada) - este teste existe para
        // travar exatamente essa regressao.
        ResponseEntity<String> produto1 = rest.getForEntity(baseUrl() + "/produtos/1", String.class);
        ResponseEntity<String> produto2 = rest.getForEntity(baseUrl() + "/produtos/2", String.class);

        assertThat(produto1.getBody()).contains("Teclado mecanico").doesNotContain("Mouse ergonomico");
        assertThat(produto2.getBody()).contains("Mouse ergonomico").doesNotContain("Teclado mecanico");
    }

    @Test
    void homeLigaParaTodosOsDemos() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/", String.class);
        assertThat(response.getBody()).contains("/contador", "/produtos", "/tarefas");
    }

    @Test
    void criarProdutoComPrecoInvalidoBloqueiaAAcao() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nome", "Webcam 1080p");
        form.add("preco", "0");

        ResponseEntity<String> response = postAction("dev.jta.demo.produtos.ProdutoNovo", "criar", form);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().get("HX-Redirect")).isNull();
        assertThat(response.getBody()).contains("Preco deve ser maior que zero");
    }

    @Test
    void criarProdutoComDadosValidosPersisteERedirecionaParaODetalhe() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nome", "Webcam 1080p");
        form.add("preco", "129.90");

        ResponseEntity<String> response = postAction("dev.jta.demo.produtos.ProdutoNovo", "criar", form);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String redirectTo = response.getHeaders().getFirst("HX-Redirect");
        assertThat(redirectTo).isNotNull().startsWith("/produtos/");

        // persistiu de verdade - aparece no catalogo servido por outro componente
        ResponseEntity<String> catalogo = rest.getForEntity(baseUrl() + "/produtos", String.class);
        assertThat(catalogo.getBody()).contains("Webcam 1080p");

        // a pagina de detalhe para onde o redirect aponta mostra o produto certo
        ResponseEntity<String> detalhe = rest.getForEntity(baseUrl() + redirectTo, String.class);
        assertThat(detalhe.getBody()).contains("Webcam 1080p", "R$ 129.90");
    }

    @Test
    void editarProdutoPreCarregaDadosExistentesEAtualizaAoSalvar() {
        // GET inicial deve vir pre-preenchido com os dados atuais (init())
        ResponseEntity<String> formulario = rest.getForEntity(baseUrl() + "/produtos/2/editar", String.class);
        assertThat(formulario.getBody()).contains("value=\"Mouse ergonomico\"");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", "2");
        form.add("nome", "Mouse ergonomico Pro");
        form.add("preco", "219.90");

        ResponseEntity<String> response = postAction("dev.jta.demo.produtos.ProdutoEditar", "salvar", form);
        assertThat(response.getHeaders().getFirst("HX-Redirect")).isEqualTo("/produtos/2");

        ResponseEntity<String> detalhe = rest.getForEntity(baseUrl() + "/produtos/2", String.class);
        assertThat(detalhe.getBody()).contains("Mouse ergonomico Pro", "R$ 219.90");
    }

    @Test
    void editarProdutoInexistenteMostraMensagemSemFormulario() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/produtos/nao-existe/editar", String.class);
        assertThat(response.getBody()).contains("Produto nao encontrado");
        assertThat(response.getBody()).doesNotContain("Salvar");
    }

    @Test
    void excluirProdutoRemoveDoCatalogoERedireciona() {
        // cria um produto so para excluir, sem afetar os dados seed usados por outros testes
        MultiValueMap<String, String> criarForm = new LinkedMultiValueMap<>();
        criarForm.add("nome", "Produto descartavel");
        criarForm.add("preco", "9.99");
        ResponseEntity<String> criado = postAction("dev.jta.demo.produtos.ProdutoNovo", "criar", criarForm);
        String pathCriado = criado.getHeaders().getFirst("HX-Redirect");
        String idCriado = pathCriado.substring(pathCriado.lastIndexOf('/') + 1);

        MultiValueMap<String, String> excluirForm = new LinkedMultiValueMap<>();
        excluirForm.add("id", idCriado);
        ResponseEntity<String> resposta = postAction("dev.jta.demo.produtos.ProdutoDetalhe", "excluir", excluirForm);

        assertThat(resposta.getHeaders().getFirst("HX-Redirect")).isEqualTo("/produtos");

        ResponseEntity<String> catalogo = rest.getForEntity(baseUrl() + "/produtos", String.class);
        assertThat(catalogo.getBody()).doesNotContain("Produto descartavel");
    }
}
