package dev.jta.runtime.csrf;

/**
 * SPI de CSRF, agnostica de framework web - mesmo espirito de
 * {@code ComponentFactory}/{@code CurrentUser} em {@code dev.jta.runtime}.
 *
 * <p><b>Mecanismo escolhido (ver SECURITY.md, achado #6):</b> double-submit
 * cookie assinado com HMAC-SHA256, stateless. O JTA renderiza no servidor,
 * entao ja sabe o valor do token no momento em que emite tanto o
 * {@code Set-Cookie} quanto o HTML - o token e escrito literalmente no
 * atributo {@code hx-headers} do {@code <body>} (ver
 * {@code PageShellRenderer}), sem nenhum JavaScript escrito pelo dev
 * precisar ler a cookie. Isso permite {@code HttpOnly=true} na cookie (mais
 * forte que o double-submit "classico" com JS lendo a cookie), e o HTMX
 * propaga {@code hx-headers} a todo pedido disparado por um elemento
 * descendente do {@code <body>} - cobrindo automaticamente todo
 * {@code hx-post} gerado pelo {@code TemplateTransformer}.
 *
 * <p>Implementacao default: {@link HmacCsrfTokenStore}. Para
 * {@code [security] csrf_mode = disabled}: {@link NoopCsrfTokenStore}.
 */
public interface CsrfTokenStore {

    /**
     * Emite (ou renova) o token para uma requisicao de pagina (GET),
     * a partir do header {@code Cookie} cru recebido (pode ser
     * {@code null}). Se a cookie ja existente for valida, o mesmo valor de
     * token e reaproveitado e {@code setCookieHeaderOrNull} vem
     * {@code null} (nao ha necessidade de reemitir a cookie no browser).
     */
    IssueResult issue(String cookieHeader);

    /**
     * Verifica uma requisicao de acao (POST): recomputa o HMAC do token
     * extraido da cookie e compara com a assinatura (prova que fomos nos
     * que emitimos), depois compara esse token com {@code submittedHeaderValue}
     * (prova que quem fez o pedido teve acesso ao HTML da pagina - logo, a
     * mesma origem).
     */
    boolean verify(String cookieHeader, String submittedHeaderValue);

    /** Nome do header HTTP que carrega o token (ex: {@code "X-JTA-CSRF-Token"}). */
    String headerName();

    /**
     * @param tokenValue           valor do token (para embutir em {@code hx-headers}),
     *                             ou {@code null} em modo {@code disabled}
     * @param setCookieHeaderOrNull valor completo do header {@code Set-Cookie} a
     *                              aplicar na resposta, ou {@code null} se
     *                              nenhuma cookie nova precisa ser emitida
     *                              (cookie existente ja valida, ou modo
     *                              {@code disabled})
     */
    record IssueResult(String tokenValue, String setCookieHeaderOrNull) {
    }
}
