package dev.jta.spring;

import dev.jta.runtime.CurrentUser;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@link CurrentUser} do adaptador Spring: traduz
 * {@code SecurityContextHolder} para a interface neutra que
 * {@code SecurityEnforcer} (jta-runtime) entende.
 *
 * <p><b>Cuidado corrigido (ver SECURITY.md, achado #3):</b>
 * {@code Authentication.isAuthenticated()} sozinho NAO distingue um
 * usuario de verdade de uma sessao anonima - o Spring Security representa
 * "ninguem logado" como um {@link AnonymousAuthenticationToken} cujo
 * {@code isAuthenticated()} retorna {@code true} por design (e um
 * "usuario anonimo autenticado como anonimo", nao a ausencia de
 * autenticacao). Excluir esse tipo explicitamente e o padrao correto e
 * bem conhecido do Spring Security para essa checagem - omiti-lo e um
 * erro comum o suficiente para ter nome proprio na comunidade.
 *
 * <p>Extraido de {@code JtaSecurityEnforcer} (jta-spring-boot-starter) na
 * extracao do nucleo agnostico - a logica de match de role em si (com o
 * prefixo {@code ROLE_}) mudou para {@code SecurityEnforcer}
 * (jta-runtime); esta classe so cuida da traducao Spring Security ->
 * {@link CurrentUser}.
 */
final class SpringCurrentUser implements CurrentUser {

    private final boolean authenticated;
    private final Set<String> authorities;

    private SpringCurrentUser(boolean authenticated, Set<String> authorities) {
        this.authenticated = authenticated;
        this.authorities = authorities;
    }

    static CurrentUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return CurrentUser.anonymous();
        }
        Set<String> authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toUnmodifiableSet());
        return new SpringCurrentUser(true, authorities);
    }

    @Override
    public boolean isAuthenticated() {
        return authenticated;
    }

    @Override
    public Set<String> authorities() {
        return authorities;
    }
}
