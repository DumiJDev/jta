package dev.jta.quarkus;

import dev.jta.runtime.CurrentUser;
import io.quarkus.arc.Arc;
import io.quarkus.security.identity.SecurityIdentity;

import java.util.Set;

/**
 * {@link CurrentUser} do adaptador Quarkus: traduz {@link SecurityIdentity}
 * (Arc/quarkus-security) para a interface neutra que {@code SecurityEnforcer}
 * (jta-runtime) entende.
 *
 * <p>Diferente do Spring Security ({@code SpringCurrentUser}, que precisa
 * excluir explicitamente {@code AnonymousAuthenticationToken}),
 * {@link SecurityIdentity#isAnonymous()} ja e a checagem correta e direta
 * para "ninguem autenticado" no modelo do Quarkus - sem a mesma pegadinha.
 */
final class QuarkusCurrentUser implements CurrentUser {

    private final boolean authenticated;
    private final Set<String> authorities;

    private QuarkusCurrentUser(boolean authenticated, Set<String> authorities) {
        this.authenticated = authenticated;
        this.authorities = authorities;
    }

    static CurrentUser current() {
        SecurityIdentity identity = Arc.container().instance(SecurityIdentity.class).get();
        if (identity == null || identity.isAnonymous()) {
            return CurrentUser.anonymous();
        }
        return new QuarkusCurrentUser(true, Set.copyOf(identity.getRoles()));
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
