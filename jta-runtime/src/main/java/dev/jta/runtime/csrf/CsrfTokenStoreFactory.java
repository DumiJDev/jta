package dev.jta.runtime.csrf;

import dev.jta.core.JtaConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Constroi o {@link CsrfTokenStore} certo a partir de {@code [security]} em
 * {@code jta.config.toml} - logica compartilhada pelos 4 adaptadores (ver
 * secao 8 do plano de CSRF nativo), para nao duplicar a resolucao de
 * segredo/modo em cada starter.
 *
 * <p>Suporta {@code csrf_mode = "native"} (default) e {@code "disabled"}.
 * {@code "delegated"} (integracao com Spring Security CSRF) fica fora de
 * escopo desta versao.
 */
public final class CsrfTokenStoreFactory {

    private static final Logger LOG = LoggerFactory.getLogger(CsrfTokenStoreFactory.class);

    private CsrfTokenStoreFactory() {
    }

    public static CsrfTokenStore create(JtaConfig config) {
        String mode = config.getString("security", "csrf_mode", "native");
        String headerName = config.getString("security", "csrf_header_name", "X-JTA-CSRF-Token");

        if ("disabled".equals(mode)) {
            return new NoopCsrfTokenStore(headerName);
        }
        if (!"native".equals(mode)) {
            throw new IllegalArgumentException(
                    "[security] csrf_mode invalido em jta.config.toml: '" + mode + "' - valores suportados nesta "
                            + "versao: \"native\" (default) ou \"disabled\". (\"delegated\" - integracao com Spring "
                            + "Security CSRF - ainda nao implementado.)");
        }

        String cookieName = config.getString("security", "csrf_cookie_name", "jta_csrf");
        boolean cookieSecure = config.getBoolean("security", "csrf_cookie_secure", false);
        byte[] secret = resolveSecret(config);
        return new HmacCsrfTokenStore(secret, cookieName, headerName, cookieSecure);
    }

    /**
     * Ordem de resolucao do segredo: (1) env var {@code JTA_CSRF_SECRET},
     * (2) {@code [security] csrf_secret} em {@code jta.config.toml}, (3)
     * gerado aleatoriamente no arranque (com aviso explicito - ver abaixo).
     */
    private static byte[] resolveSecret(JtaConfig config) {
        String envSecret = System.getenv("JTA_CSRF_SECRET");
        if (envSecret != null && !envSecret.isBlank()) {
            return envSecret.getBytes(StandardCharsets.UTF_8);
        }

        String configSecret = config.getString("security", "csrf_secret", "");
        if (!configSecret.isBlank()) {
            return configSecret.getBytes(StandardCharsets.UTF_8);
        }

        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        LOG.warn("Nenhum segredo CSRF configurado (nem env JTA_CSRF_SECRET, nem [security] csrf_secret em "
                + "jta.config.toml) - gerando um segredo aleatorio no arranque. Isso e INACEITAVEL em producao: "
                + "(1) quebra toda cookie de CSRF emitida a cada redeploy/reinicio (usuarios com uma pagina aberta "
                + "veem os proximos POSTs falharem com 403 ate recarregar); (2) com multiplas instancias atras de "
                + "um load balancer, cada instancia gera um segredo DIFERENTE - um token emitido por uma instancia "
                + "nunca vai bater a verificacao noutra. Configure JTA_CSRF_SECRET (ou [security] csrf_secret) antes "
                + "de ir para producao.");
        return random;
    }
}
