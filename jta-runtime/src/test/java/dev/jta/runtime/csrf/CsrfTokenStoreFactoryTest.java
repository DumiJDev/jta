package dev.jta.runtime.csrf;

import dev.jta.core.JtaConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsrfTokenStoreFactoryTest {

    @Test
    void defaultSemConfigENativeComHeaderPadrao() {
        CsrfTokenStore store = CsrfTokenStoreFactory.create(JtaConfig.empty());
        assertInstanceOf(HmacCsrfTokenStore.class, store);
        assertEquals("X-JTA-CSRF-Token", store.headerName());
    }

    @Test
    void csrfModeDisabledDevolveNoop() {
        JtaConfig config = JtaConfig.parse("""
                [security]
                csrf_mode = "disabled"
                """);
        CsrfTokenStore store = CsrfTokenStoreFactory.create(config);
        assertInstanceOf(NoopCsrfTokenStore.class, store);
        assertTrue(store.verify(null, null));
    }

    @Test
    void csrfModeInvalidoLancaExcecao() {
        JtaConfig config = JtaConfig.parse("""
                [security]
                csrf_mode = "delegated"
                """);
        assertThrows(IllegalArgumentException.class, () -> CsrfTokenStoreFactory.create(config));
    }

    @Test
    void secretDoConfigEUsadoQuandoPresente() {
        JtaConfig config = JtaConfig.parse("""
                [security]
                csrf_secret = "segredo-fixo-de-configuracao-123456"
                """);
        HmacCsrfTokenStore store = (HmacCsrfTokenStore) CsrfTokenStoreFactory.create(config);
        // token emitido com um segredo fixo deve ser verificavel por outra
        // instancia construida com o MESMO segredo (prova indireta de que o
        // segredo do config foi realmente usado, nao um aleatorio).
        HmacCsrfTokenStore other = (HmacCsrfTokenStore) CsrfTokenStoreFactory.create(config);
        CsrfTokenStore.IssueResult issued = store.issue(null);
        String cookieHeader = "jta_csrf=" + issued.setCookieHeaderOrNull()
                .substring("jta_csrf=".length())
                .split(";", 2)[0];
        assertTrue(other.verify(cookieHeader, issued.tokenValue()));
    }

    @Test
    void nomesCustomizadosDeCookieEHeaderSaoRespeitados() {
        JtaConfig config = JtaConfig.parse("""
                [security]
                csrf_secret = "segredo-fixo-de-configuracao-123456"
                csrf_cookie_name = "meu_csrf"
                csrf_header_name = "X-Meu-Token"
                """);
        CsrfTokenStore store = CsrfTokenStoreFactory.create(config);
        assertEquals("X-Meu-Token", store.headerName());
        CsrfTokenStore.IssueResult issued = store.issue(null);
        assertTrue(issued.setCookieHeaderOrNull().startsWith("meu_csrf="));
    }
}
