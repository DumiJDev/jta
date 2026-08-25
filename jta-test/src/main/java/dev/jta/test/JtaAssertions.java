package dev.jta.test;

import dev.jta.runtime.ActionResult;
import dev.jta.runtime.PageResult;

/**
 * Asserts prontos sobre os tipos selados {@link PageResult}/
 * {@link ActionResult} devolvidos por {@link JtaTestHarness}.
 *
 * <p>Lanca {@link AssertionError} puro (tipo do proprio JDK) em vez de
 * depender de uma lib de assercao especifica (JUnit, AssertJ, Hamcrest) -
 * qualquer runner de teste reconhece {@link AssertionError} como falha,
 * entao este modulo nao precisa escolher um runner por quem o usa.
 */
public final class JtaAssertions {

    private JtaAssertions() {
    }

    /** Verifica que a acao foi renderizada com sucesso e devolve o HTML do fragmento. */
    public static String assertRendered(ActionResult result) {
        if (result instanceof ActionResult.Rendered rendered) {
            return rendered.html();
        }
        throw new AssertionError("Esperava ActionResult.Rendered, mas foi " + describe(result));
    }

    /** Verifica que a pagina foi renderizada com sucesso e devolve o HTML do documento completo. */
    public static String assertRendered(PageResult result) {
        if (result instanceof PageResult.Rendered rendered) {
            return rendered.html();
        }
        throw new AssertionError("Esperava PageResult.Rendered, mas foi " + describe(result));
    }

    /** Verifica que a acao foi negada por {@code @RequiresRole}. */
    public static void assertForbidden(ActionResult result) {
        if (!(result instanceof ActionResult.Forbidden)) {
            throw new AssertionError("Esperava ActionResult.Forbidden, mas foi " + describe(result));
        }
    }

    /** Verifica que a pagina foi negada por {@code @RequiresRole}. */
    public static void assertForbidden(PageResult result) {
        if (!(result instanceof PageResult.Forbidden)) {
            throw new AssertionError("Esperava PageResult.Forbidden, mas foi " + describe(result));
        }
    }

    /** Verifica que a acao nao existe no componente (nome fora de {@code ComponentMetadata.actions()}). */
    public static void assertNotFound(ActionResult result) {
        if (!(result instanceof ActionResult.NotFound)) {
            throw new AssertionError("Esperava ActionResult.NotFound, mas foi " + describe(result));
        }
    }

    /** Verifica que a acao lancou {@link dev.jta.core.Redirect} e devolve o path de destino. */
    public static String assertRedirect(ActionResult result) {
        if (result instanceof ActionResult.Redirect redirect) {
            return redirect.path();
        }
        throw new AssertionError("Esperava ActionResult.Redirect, mas foi " + describe(result));
    }

    /** Verifica que {@code html} contem {@code snippet} (comparacao literal, sem regex/normalizacao). */
    public static void assertContains(String html, String snippet) {
        if (html == null || !html.contains(snippet)) {
            throw new AssertionError("Esperava que o HTML contivesse '" + snippet + "', mas nao continha:\n" + html);
        }
    }

    /** Verifica que {@code html} NAO contem {@code snippet}. */
    public static void assertDoesNotContain(String html, String snippet) {
        if (html != null && html.contains(snippet)) {
            throw new AssertionError("Esperava que o HTML NAO contivesse '" + snippet + "', mas continha:\n" + html);
        }
    }

    private static String describe(ActionResult result) {
        return result == null ? "null" : result.getClass().getSimpleName();
    }

    private static String describe(PageResult result) {
        return result == null ? "null" : result.getClass().getSimpleName();
    }
}
