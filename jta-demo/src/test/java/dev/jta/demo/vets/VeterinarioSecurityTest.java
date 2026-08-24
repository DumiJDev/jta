package dev.jta.demo.vets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Primeira cobertura de integracao real de {@code @RequiresRole}/
 * {@code JtaSecurityEnforcer} (jta-runtime {@code SecurityEnforcer}
 * agora) contra um app rodando de verdade com Spring Security
 * configurado - antes desta reforma do demo, essa checagem so era
 * exercitada em compile-time por {@code scripts/smoke-test.sh}. Prova as
 * tres respostas possiveis de {@code SecurityEnforcer.isAuthorized}:
 * sem autenticacao, autenticado com a role errada, autenticado com a
 * role certa.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VeterinarioSecurityTest {

    @LocalServerPort
    private int port;

    private final TestRestTemplate rest = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void listaDeVeterinariosEAbertaSemLogin() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/veterinarios", String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void cadastroDeVeterinarioSemLoginENegado() {
        ResponseEntity<String> response = rest.getForEntity(baseUrl() + "/veterinarios/novo", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cadastroDeVeterinarioComRoleErradaENegado() {
        ResponseEntity<String> response = rest.withBasicAuth("user", "user")
                .getForEntity(baseUrl() + "/veterinarios/novo", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cadastroDeVeterinarioComRoleAdminEPermitido() {
        ResponseEntity<String> response = rest.withBasicAuth("admin", "admin")
                .getForEntity(baseUrl() + "/veterinarios/novo", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("Novo veterinario");
    }
}
