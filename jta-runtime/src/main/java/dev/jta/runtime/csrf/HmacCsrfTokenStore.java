package dev.jta.runtime.csrf;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Implementacao default de {@link CsrfTokenStore}: double-submit cookie
 * assinado com HMAC-SHA256 (ver SECURITY.md, achado #6). Stateless e
 * zero dependencias novas ({@code javax.crypto.Mac} e JDK puro) - funciona
 * mesmo em hosts sem sessao de servidor por baixo (ex: jta-standalone).
 *
 * <p>Formato da cookie: {@code token.assinatura}, onde
 * {@code assinatura = base64url(HMAC-SHA256(secret, token))} e
 * {@code token = base64url(32 bytes aleatorios)}. Ambos base64url
 * sem padding, entao nunca contem o caractere {@code '.'} usado como
 * separador.
 */
public final class HmacCsrfTokenStore implements CsrfTokenStore {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final byte[] secret;
    private final String cookieName;
    private final String headerName;
    private final boolean cookieSecure;

    /**
     * @param secret       segredo HMAC - ver ordem de resolucao documentada em
     *                     cada adaptador (env var {@code JTA_CSRF_SECRET} > config > gerado)
     * @param cookieName   nome da cookie (ex: {@code "jta_csrf"})
     * @param headerName   nome do header HTTP esperado (ex: {@code "X-JTA-CSRF-Token"})
     * @param cookieSecure se {@code true}, emite a cookie com o atributo {@code Secure}
     *                     (exige HTTPS - deve ser {@code true} em producao com TLS)
     */
    public HmacCsrfTokenStore(byte[] secret, String cookieName, String headerName, boolean cookieSecure) {
        if (secret == null || secret.length == 0) {
            throw new IllegalArgumentException("Segredo CSRF nao pode ser vazio");
        }
        this.secret = secret.clone();
        this.cookieName = cookieName;
        this.headerName = headerName;
        this.cookieSecure = cookieSecure;
    }

    @Override
    public IssueResult issue(String cookieHeader) {
        String existingToken = extractValidToken(cookieHeader);
        if (existingToken != null) {
            // cookie ja presente e com assinatura valida - reaproveita o
            // mesmo token, sem reemitir Set-Cookie (evita churn desnecessario
            // a cada GET de pagina).
            return new IssueResult(existingToken, null);
        }
        String token = newToken();
        String cookieValue = token + "." + sign(token);
        StringBuilder setCookie = new StringBuilder()
                .append(cookieName).append('=').append(cookieValue)
                .append("; Path=/; HttpOnly; SameSite=Lax");
        if (cookieSecure) {
            setCookie.append("; Secure");
        }
        return new IssueResult(token, setCookie.toString());
    }

    @Override
    public boolean verify(String cookieHeader, String submittedHeaderValue) {
        if (submittedHeaderValue == null || submittedHeaderValue.isBlank()) {
            return false;
        }
        String cookieToken = extractValidToken(cookieHeader);
        if (cookieToken == null) {
            return false;
        }
        return constantTimeEquals(cookieToken, submittedHeaderValue);
    }

    @Override
    public String headerName() {
        return headerName;
    }

    /** Extrai o token da cookie e valida a assinatura; {@code null} se ausente/invalido. */
    private String extractValidToken(String cookieHeader) {
        String raw = extractCookieValue(cookieHeader, cookieName);
        if (raw == null) {
            return null;
        }
        int dot = raw.indexOf('.');
        if (dot < 0 || dot == raw.length() - 1) {
            return null;
        }
        String token = raw.substring(0, dot);
        String signature = raw.substring(dot + 1);
        String expected = sign(token);
        if (!constantTimeEquals(signature, expected)) {
            return null;
        }
        return token;
    }

    private static String extractCookieValue(String cookieHeader, String name) {
        if (cookieHeader == null || cookieHeader.isBlank()) {
            return null;
        }
        for (String part : cookieHeader.split(";")) {
            String pair = part.trim();
            int eq = pair.indexOf('=');
            if (eq < 0) {
                continue;
            }
            if (pair.substring(0, eq).trim().equals(name)) {
                return pair.substring(eq + 1).trim();
            }
        }
        return null;
    }

    private static String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return ENCODER.encodeToString(bytes);
    }

    private String sign(String token) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] out = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
            return ENCODER.encodeToString(out);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao assinar token CSRF", e);
        }
    }

    /** Comparacao em tempo constante - evita timing attack contra a assinatura/token. */
    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
