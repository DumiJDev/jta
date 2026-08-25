package dev.jta.runtime.csrf;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@link CsrfTokenStore} para {@code [security] csrf_mode = disabled} -
 * nao emite nenhuma cookie/{@code hx-headers} e aceita qualquer POST sem
 * verificacao. Existe para o caso raro de outra camada (ex: Spring
 * Security CSRF, modo {@code delegated} - fora de escopo desta versao) ja
 * cobrir a protecao, ou para debugging local deliberado.
 */
public final class NoopCsrfTokenStore implements CsrfTokenStore {

    private static final Logger LOG = LoggerFactory.getLogger(NoopCsrfTokenStore.class);

    private final String headerName;

    public NoopCsrfTokenStore(String headerName) {
        this.headerName = headerName;
        LOG.warn("CSRF desabilitado ([security] csrf_mode = \"disabled\") - todo POST de acao e aceito sem "
                + "verificacao de origem. So use isto se outra camada ja cobre CSRF, ou em ambiente de "
                + "desenvolvimento local deliberadamente.");
    }

    @Override
    public IssueResult issue(String cookieHeader) {
        return new IssueResult(null, null);
    }

    @Override
    public boolean verify(String cookieHeader, String submittedHeaderValue) {
        return true;
    }

    @Override
    public String headerName() {
        return headerName;
    }
}
