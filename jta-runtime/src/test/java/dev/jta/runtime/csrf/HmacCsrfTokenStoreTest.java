package dev.jta.runtime.csrf;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ver SECURITY.md, achado #6 - CSRF nativo (double-submit cookie assinada com HMAC-SHA256). */
class HmacCsrfTokenStoreTest {

    private static final byte[] SECRET = "segredo-de-teste-bem-longo-1234567890".getBytes(StandardCharsets.UTF_8);

    private HmacCsrfTokenStore store() {
        return new HmacCsrfTokenStore(SECRET, "jta_csrf", "X-JTA-CSRF-Token", false);
    }

    @Test
    void issueSemCookieExistenteGeraTokenNovoESetCookie() {
        CsrfTokenStore.IssueResult result = store().issue(null);
        assertNotNull(result.tokenValue());
        assertNotNull(result.setCookieHeaderOrNull());
        assertTrue(result.setCookieHeaderOrNull().startsWith("jta_csrf="));
        assertTrue(result.setCookieHeaderOrNull().contains("HttpOnly"));
        assertTrue(result.setCookieHeaderOrNull().contains("SameSite=Lax"));
        assertFalse(result.setCookieHeaderOrNull().contains("Secure"));
    }

    @Test
    void issueComCookieValidaReaproveitaOMesmoTokenSemReemitirSetCookie() {
        HmacCsrfTokenStore store = store();
        CsrfTokenStore.IssueResult first = store.issue(null);
        String cookieHeader = "jta_csrf=" + extractCookieValue(first.setCookieHeaderOrNull());

        CsrfTokenStore.IssueResult second = store.issue(cookieHeader);
        assertEquals(first.tokenValue(), second.tokenValue());
        assertNull(second.setCookieHeaderOrNull());
    }

    @Test
    void issueComCookieInvalidaGeraTokenNovo() {
        HmacCsrfTokenStore store = store();
        CsrfTokenStore.IssueResult first = store.issue(null);
        String forgedCookieHeader = "jta_csrf=" + first.tokenValue() + ".assinatura-invalida";

        CsrfTokenStore.IssueResult second = store.issue(forgedCookieHeader);
        assertNotEquals(first.tokenValue(), second.tokenValue());
        assertNotNull(second.setCookieHeaderOrNull());
    }

    @Test
    void verifyComCookieETokenCorretosPassa() {
        HmacCsrfTokenStore store = store();
        CsrfTokenStore.IssueResult issued = store.issue(null);
        String cookieHeader = "jta_csrf=" + extractCookieValue(issued.setCookieHeaderOrNull());

        assertTrue(store.verify(cookieHeader, issued.tokenValue()));
    }

    @Test
    void verifySemCookieFalha() {
        HmacCsrfTokenStore store = store();
        CsrfTokenStore.IssueResult issued = store.issue(null);
        assertFalse(store.verify(null, issued.tokenValue()));
    }

    @Test
    void verifySemHeaderFalha() {
        HmacCsrfTokenStore store = store();
        CsrfTokenStore.IssueResult issued = store.issue(null);
        String cookieHeader = "jta_csrf=" + extractCookieValue(issued.setCookieHeaderOrNull());
        assertFalse(store.verify(cookieHeader, null));
        assertFalse(store.verify(cookieHeader, ""));
    }

    @Test
    void verifyComCookieForjadaDeOutraOrigemFalha() {
        // simula uma cookie assinada com OUTRO segredo (outra instancia de
        // HmacCsrfTokenStore, como se fosse outra origem/servidor).
        HmacCsrfTokenStore attacker = new HmacCsrfTokenStore(
                "outro-segredo-completamente-diferente-999".getBytes(StandardCharsets.UTF_8),
                "jta_csrf", "X-JTA-CSRF-Token", false);
        CsrfTokenStore.IssueResult forged = attacker.issue(null);
        String forgedCookieHeader = "jta_csrf=" + extractCookieValue(forged.setCookieHeaderOrNull());

        HmacCsrfTokenStore victim = store();
        assertFalse(victim.verify(forgedCookieHeader, forged.tokenValue()));
    }

    @Test
    void verifyComHeaderQueNaoBateComOTokenDaCookieFalha() {
        HmacCsrfTokenStore store = store();
        CsrfTokenStore.IssueResult issued = store.issue(null);
        String cookieHeader = "jta_csrf=" + extractCookieValue(issued.setCookieHeaderOrNull());

        assertFalse(store.verify(cookieHeader, issued.tokenValue() + "adulterado"));
    }

    @Test
    void cookieSecureQuandoConfigurado() {
        HmacCsrfTokenStore store = new HmacCsrfTokenStore(SECRET, "jta_csrf", "X-JTA-CSRF-Token", true);
        CsrfTokenStore.IssueResult result = store.issue(null);
        assertTrue(result.setCookieHeaderOrNull().contains("; Secure"));
    }

    @Test
    void segredoVazioRejeitadoNoConstrutor() {
        assertThrows(IllegalArgumentException.class, () -> new HmacCsrfTokenStore(new byte[0], "jta_csrf", "X", false));
        assertThrows(IllegalArgumentException.class, () -> new HmacCsrfTokenStore(null, "jta_csrf", "X", false));
    }

    @Test
    void headerNameDevolveOConfigurado() {
        assertEquals("X-JTA-CSRF-Token", store().headerName());
    }

    private static String extractCookieValue(String setCookieHeader) {
        String withoutName = setCookieHeader.substring("jta_csrf=".length());
        int semicolon = withoutName.indexOf(';');
        return semicolon < 0 ? withoutName : withoutName.substring(0, semicolon);
    }
}
