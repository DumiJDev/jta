package dev.jta.runtime;

/**
 * Resultado neutro de framework de {@link JtaPageDispatcher#dispatch}.
 * Cada adaptador traduz isto para o tipo de resposta do seu framework web -
 * ver {@code JtaRouteRegistrar} em jta-spring-boot-starter.
 */
public sealed interface PageResult {

    /** {@code @RequiresRole} negou acesso a pagina. */
    record Forbidden() implements PageResult {
    }

    /**
     * Documento HTML completo (ja envolvido por {@code PageShellRenderer}).
     *
     * @param html               documento completo, incluindo o token CSRF
     *                           embutido em {@code hx-headers} quando aplicavel
     * @param csrfSetCookieHeader valor completo do header {@code Set-Cookie}
     *                           para a cookie de CSRF, a aplicar na resposta
     *                           pelo adaptador - ou {@code null} se nenhuma
     *                           cookie nova precisa ser emitida (ver
     *                           {@code CsrfTokenStore#issue})
     */
    record Rendered(String html, String csrfSetCookieHeader) implements PageResult {
    }
}
