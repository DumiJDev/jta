package dev.jta.runtime.csrf;

/**
 * Token CSRF pronto para ser embutido no HTML da pagina (atributo
 * {@code hx-headers} do {@code <body>} - ver {@code PageShellRenderer}).
 * {@code headerName} vem de {@link CsrfTokenStore#headerName()}, para que o
 * HTMX propague automaticamente o mesmo cabecalho que
 * {@link CsrfTokenStore#verify} vai checar em toda acao HTMX disparada por
 * um elemento descendente do {@code <body>}.
 */
public record CsrfToken(String headerName, String value) {
}
