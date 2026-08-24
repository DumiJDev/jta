package dev.jta.runtime;

import java.util.Set;

/**
 * Representacao minima e agnostica de framework do usuario da requisicao
 * atual, o suficiente para {@link SecurityEnforcer} decidir
 * {@code @RequiresRole}. Cada adaptador traduz seu proprio modelo de
 * autenticacao para esta interface - ver {@code SpringCurrentUser} em
 * jta-spring-boot-starter, que le {@code SecurityContextHolder} e ja
 * resolve a distincao entre "sem autenticacao" e "autenticado como
 * anonimo" (ver SECURITY.md, achado #3) antes de chegar aqui.
 */
public interface CurrentUser {

    boolean isAuthenticated();

    /** Nomes de authority/role do usuario (ex: {@code "ADMIN"}, {@code "ROLE_ADMIN"}). */
    Set<String> authorities();

    static CurrentUser anonymous() {
        return AnonymousCurrentUser.INSTANCE;
    }
}
