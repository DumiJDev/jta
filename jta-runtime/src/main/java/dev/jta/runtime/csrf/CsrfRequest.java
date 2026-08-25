package dev.jta.runtime.csrf;

/**
 * Par bruto (cookie + header) extraido de uma requisicao de acao HTMX,
 * repassado a {@link CsrfTokenStore#verify} por {@code JtaActionDispatcher}.
 *
 * @param cookieHeader valor cru do header HTTP {@code Cookie} recebido, ou
 *                      {@code null} se a requisicao nao enviou nenhum
 * @param headerValue  valor do header de CSRF ({@link CsrfTokenStore#headerName()}),
 *                      ou {@code null} se ausente
 */
public record CsrfRequest(String cookieHeader, String headerValue) {

    /** Conveniencia para adaptadores que ainda nao tem nenhum dos dois valores em maos (ex: modo desabilitado). */
    public static CsrfRequest of(String cookieHeader, String headerValue) {
        return new CsrfRequest(cookieHeader, headerValue);
    }
}
