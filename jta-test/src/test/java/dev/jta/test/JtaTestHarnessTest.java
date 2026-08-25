package dev.jta.test;

import dev.jta.runtime.ActionResult;
import dev.jta.runtime.PageResult;
import dev.jta.test.fixture.Contador;
import dev.jta.test.fixture.PainelAdmin;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prova de que {@link JtaTestHarness} exercita o mesmo caminho de codigo
 * que roda em producao (JtaPageDispatcher/JtaActionDispatcher de
 * jta-runtime), sem HTTP nenhum por baixo.
 */
class JtaTestHarnessTest {

    private final JtaTestHarness harness = JtaTestHarness.forClasspath();

    @Test
    void renderizaPaginaComEstadoInicial() {
        PageResult result = harness.renderPage(Contador.class);

        String html = JtaAssertions.assertRendered(result);
        JtaAssertions.assertContains(html, "htmx.org");
        JtaAssertions.assertContains(html, ">0<");
    }

    @Test
    void invocaAcaoReidrataEstadoARenderizaFragmento() {
        ActionResult result = harness.invokeAction(Contador.class, "incrementar",
                Map.of("valor", new String[]{"5"}), dev.jta.runtime.CurrentUser.anonymous());

        String html = JtaAssertions.assertRendered(result);
        JtaAssertions.assertContains(html, ">6<");
        JtaAssertions.assertDoesNotContain(html, "<!DOCTYPE html>");
    }

    @Test
    void acaoNaoDeclaradaDevolveNotFound() {
        ActionResult result = harness.invokeAction(Contador.class, "metodoQueNaoExisteComoAcao");

        JtaAssertions.assertNotFound(result);
    }

    @Test
    void paginaRestritaNegaAcessoAUsuarioAnonimo() {
        PageResult result = harness.renderPage(PainelAdmin.class, Map.of(), Map.of(),
                TestCurrentUser.anonymous());

        JtaAssertions.assertForbidden(result);
    }

    @Test
    void paginaRestritaNegaAcessoAUsuarioSemARoleCerta() {
        PageResult result = harness.renderPage(PainelAdmin.class, Map.of(), Map.of(),
                TestCurrentUser.withRoles("PROFESSOR"));

        JtaAssertions.assertForbidden(result);
    }

    @Test
    void paginaRestritaLiberaAcessoComARoleCerta() {
        PageResult result = harness.renderPage(PainelAdmin.class, Map.of(), Map.of(),
                TestCurrentUser.withRoles("ADMIN"));

        String html = JtaAssertions.assertRendered(result);
        JtaAssertions.assertContains(html, "painel restrito");
    }

    @Test
    void newInstanceCriaComponenteSemPopularEstado() {
        Contador contador = harness.newInstance(Contador.class);

        assertEquals(0, contador.valor);
    }

    @Test
    void assertRenderedFalhaQuandoResultadoNaoEHRendered() {
        ActionResult forbidden = new ActionResult.Forbidden();

        assertThrows(AssertionError.class, () -> JtaAssertions.assertRendered(forbidden));
    }
}
