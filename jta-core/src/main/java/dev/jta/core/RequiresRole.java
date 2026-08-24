package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Restringe o acesso a um componente (pagina, via {@code @Route}, ou
 * qualquer acao dentro dele) a usuarios com pelo menos uma das roles
 * listadas.
 *
 * <p>Se {@code jta.config.toml} configurar {@code [security] roles_enum}
 * apontando para um enum, cada valor aqui e validado em compile-time
 * contra as constantes desse enum (erro de digitacao vira erro de build,
 * nao 403 silencioso descoberto em producao). Sem essa config, os
 * valores sao aceitos como strings livres, sem validacao.
 *
 * <pre>{@code
 * @Route("/admin")
 * @RequiresRole("ADMIN")
 * public class AdminPage { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface RequiresRole {
    String[] value();
}
