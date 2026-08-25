package dev.jta.test;

import dev.jta.runtime.CurrentUser;

import java.util.Set;

/**
 * {@link CurrentUser} fake para testar {@code @RequiresRole}/
 * {@code @AllowAnonymous} sem depender de nenhum provedor de autenticacao
 * real (Spring Security, etc.) no classpath de teste.
 *
 * <p>Aceita tanto nomes puros de role ({@code "ADMIN"}) quanto o padrao
 * {@code ROLE_} do Spring Security ({@code "ROLE_ADMIN"}) - mesma
 * flexibilidade de {@code SecurityEnforcer} em jta-runtime, entao um teste
 * pode usar qualquer uma das duas convencoes e o resultado bate com o que
 * aconteceria em producao.
 */
public record TestCurrentUser(boolean isAuthenticated, Set<String> authorities) implements CurrentUser {

    /** Usuario sem autenticacao nenhuma - equivalente a {@link CurrentUser#anonymous()}. */
    public static TestCurrentUser anonymous() {
        return new TestCurrentUser(false, Set.of());
    }

    /** Usuario autenticado, sem nenhuma role concedida (falha em qualquer {@code @RequiresRole}). */
    public static TestCurrentUser authenticated() {
        return new TestCurrentUser(true, Set.of());
    }

    /** Usuario autenticado com as roles informadas (nomes puros, ex: {@code "ADMIN"}). */
    public static TestCurrentUser withRoles(String... roles) {
        return new TestCurrentUser(true, Set.of(roles));
    }
}
