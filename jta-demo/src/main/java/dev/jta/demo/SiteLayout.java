package dev.jta.demo;

import dev.jta.core.Layout;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Layout comum de toda pagina (ver {@code src/main/resources/jta-templates/dev/jta/demo/SiteLayout.jta}
 * e {@code SiteLayout.css}) - composicao via {@code @Route(layout = SiteLayout.class)}
 * + {@code <router-outlet/>}.
 *
 * <p>Le {@code SecurityContextHolder} diretamente (mesma checagem de
 * {@code SpringCurrentUser}, incluindo excluir {@code AnonymousAuthenticationToken})
 * so para decidir o que mostrar no nav (badge de role, "Entrar" vs "Sair") -
 * nao e usado para nenhuma decisao de autorizacao, que continua
 * inteiramente a cargo de {@code @RequiresRole}/{@code SecurityEnforcer}.
 */
@Layout(templateUrl = "SiteLayout.jta", styleUrl = "SiteLayout.css")
public class SiteLayout {

    private Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return auth;
    }

    public boolean autenticado() {
        return authentication() != null;
    }

    public String usuario() {
        Authentication auth = authentication();
        return auth != null ? auth.getName() : "";
    }

    /** Primeira role do usuario, sem o prefixo {@code ROLE_} do Spring Security, para exibir como badge no nav. */
    public String role() {
        Authentication auth = authentication();
        if (auth == null) {
            return "";
        }
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .findFirst()
                .orElse("");
    }
}
