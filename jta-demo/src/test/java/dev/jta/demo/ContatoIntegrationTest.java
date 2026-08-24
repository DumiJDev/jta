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
 * Prova de ponta a ponta de Jakarta Validation: submissao invalida nao
 * deve invocar a acao (nao deve mostrar "Mensagem enviada"), e deve
 * mostrar as mensagens de erro declaradas nas anotacoes @NotBlank/@Email.
 * Submissao valida deve invocar a acao normalmente.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContatoIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private ResponseEntity<String> enviar(String nome, String email) {
        String selector = SelectorDerivation.derive("dev.jta.demo.Contato");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nome", nome);
        form.add("email", email);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        return rest.exchange(baseUrl() + "/__jta/action/" + selector + "?action=enviar",
                HttpMethod.POST, request, String.class);
    }

    @Test
    void submissaoComCamposVaziosBloqueiaAAcaoEMostraErros() {
        ResponseEntity<String> response = enviar("", "");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        String body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body).doesNotContain("Mensagem enviada");
        assertThat(body).contains("Nome e obrigatorio");
        assertThat(body).contains("Email e obrigatorio");
    }

    @Test
    void emailInvalidoBloqueiaAAcao() {
        ResponseEntity<String> response = enviar("Ana", "nao-e-um-email");

        assertThat(response.getBody()).doesNotContain("Mensagem enviada");
        assertThat(response.getBody()).contains("Email invalido");
    }

    @Test
    void submissaoValidaInvocaAAcao() {
        ResponseEntity<String> response = enviar("Ana", "ana@example.com");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Mensagem enviada, obrigado!");
    }
}
