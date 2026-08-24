package dev.jta.demo;

/**
 * Roles validas do demo, referenciadas por {@code [security] roles_enum}
 * em {@code jta.config.toml} - toda role usada em {@code @RequiresRole}
 * e validada contra estas constantes em compile-time (com sugestao
 * "voce quis dizer X?" se houver erro de digitacao).
 */
public enum AppRole {
    ADMIN,
    PROFESSOR
}
