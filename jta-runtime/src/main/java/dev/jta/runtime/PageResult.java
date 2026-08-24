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

    /** Documento HTML completo (ja envolvido por {@code PageShellRenderer}). */
    record Rendered(String html) implements PageResult {
    }
}
