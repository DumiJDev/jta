package dev.jta.demo.turmas;

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

/** Catalogo publico (@AllowAnonymous) + edicao por id com {@code init()} pre-carregando dados existentes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class TurmaIntegrationTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate admin = new TestRestTemplate().withBasicAuth("admin", "admin");

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    private static final Pattern CSRF_TOKEN_PATTERN =
            Pattern.compile("hx-headers='\\{\"X-JTA-CSRF-Token\":\"([^\"]+)\"}'");

    /** Fluxo real de CSRF nativo (ver SECURITY.md, achado #6) - ver equivalente em AlunoIntegrationTest. */
    private ResponseEntity<String> postAction(String fqn, String action, MultiValueMap<String, String> form) {
        ResponseEntity<String> pagina = admin.getForEntity(baseUrl() + "/turmas", String.class);
        List<String> setCookies = pagina.getHeaders().get(HttpHeaders.SET_COOKIE);
        String cookie = (setCookies == null ? List.<String>of() : setCookies).stream()
                .filter(v -> v.startsWith("jta_csrf="))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("GET /turmas nao emitiu Set-Cookie de CSRF: " + setCookies))
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
        return admin.exchange(baseUrl() + "/__jta/action/" + selector + "?action=" + action,
                HttpMethod.POST, request, String.class);
    }

    @Test
    void catalogoPublicoListaTurmasSemLogin() {
        ResponseEntity<String> response = new TestRestTemplate().getForEntity(baseUrl() + "/turmas", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("9-A", "9-B");
    }

    @Test
    void editarTurmaPreCarregaDadosExistentesEAtualizaAoSalvar() {
        ResponseEntity<String> formulario = admin.getForEntity(baseUrl() + "/turmas/1/editar", String.class);
        assertThat(formulario.getBody()).contains("value=\"9-A\"");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("id", "1");
        form.add("nome", "9-A (renomeada)");
        form.add("ano", "2026");

        ResponseEntity<String> response = postAction("dev.jta.demo.turmas.TurmaEditar", "salvar", form);
        assertThat(response.getHeaders().getFirst("HX-Redirect")).isEqualTo("/turmas");

        ResponseEntity<String> lista = admin.getForEntity(baseUrl() + "/turmas", String.class);
        assertThat(lista.getBody()).contains("9-A (renomeada)");
    }

    @Test
    void criarTurmaComAnoVazioBloqueiaAAcao() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("nome", "10-A");
        form.add("ano", "");

        ResponseEntity<String> response = postAction("dev.jta.demo.turmas.TurmaNovo", "criar", form);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().get("HX-Redirect")).isNull();
        assertThat(response.getBody()).contains("Ano letivo e obrigatorio");
    }
}
