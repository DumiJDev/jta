package dev.jta.runtime.csrf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ver plano de CSRF, {@code [security] csrf_mode = "disabled"}: exercita em
 * isolamento (sem subir nenhum servidor HTTP - "contexto de teste
 * separado") o exato mecanismo que {@code JtaActionDispatcher} usa para
 * decidir se aceita um POST sem token: delega inteiramente a
 * {@link CsrfTokenStore#verify}, que aqui sempre devolve {@code true}.
 */
class NoopCsrfTokenStoreTest {

    @Test
    void verifyAceitaQualquerCombinacaoIncluindoAusenciaTotalDeCookieEHeader() {
        NoopCsrfTokenStore store = new NoopCsrfTokenStore("X-JTA-CSRF-Token");
        assertTrue(store.verify(null, null));
        assertTrue(store.verify("qualquer-coisa", null));
        assertTrue(store.verify(null, "qualquer-coisa"));
        assertTrue(store.verify("cookie-invalido", "header-invalido"));
    }

    @Test
    void issueNaoEmiteNenhumaCookieNemToken() {
        NoopCsrfTokenStore store = new NoopCsrfTokenStore("X-JTA-CSRF-Token");
        CsrfTokenStore.IssueResult result = store.issue("qualquer-cookie-recebida");
        assertNull(result.tokenValue());
        assertNull(result.setCookieHeaderOrNull());
    }

    @Test
    void headerNameDevolveOConfigurado() {
        NoopCsrfTokenStore store = new NoopCsrfTokenStore("X-Outro-Header");
        assertEquals("X-Outro-Header", store.headerName());
    }
}
