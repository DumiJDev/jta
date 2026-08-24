package dev.jta.runtime;

/**
 * Resultado neutro de framework de {@link JtaActionDispatcher#dispatch}.
 * Cada adaptador traduz isto para o tipo de resposta do seu framework web
 * (ex: {@code ResponseEntity} no Spring) - ver {@code JtaActionController}
 * em jta-spring-boot-starter.
 */
public sealed interface ActionResult {

    /** {@code @RequiresRole} negou acesso ao componente. */
    record Forbidden() implements ActionResult {
    }

    /**
     * {@code action} nao esta em {@code ComponentMetadata.actions()} - ver
     * SECURITY.md, achado #1 (invocacao de metodo arbitrario).
     */
    record NotFound() implements ActionResult {
    }

    /** A acao lancou {@link dev.jta.core.Redirect}; navegar para {@code path}. */
    record Redirect(String path) implements ActionResult {
    }

    /** Fragmento HTML renderizado apos a acao (ou apos falha de validacao). */
    record Rendered(String html) implements ActionResult {
    }
}
