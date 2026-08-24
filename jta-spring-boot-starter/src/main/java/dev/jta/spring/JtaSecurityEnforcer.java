package dev.jta.spring;

import dev.jta.core.ComponentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Verifica {@code @RequiresRole} contra a autenticacao atual do Spring
 * Security. Um componente sem {@code @RequiresRole} (a maioria) nunca
 * passa por aqui de verdade - {@link ComponentMetadata#isRestricted()}
 * e checado primeiro pelos chamadores ({@code JtaRouteRegistrar},
 * {@code JtaActionController}), entao o custo desta classe so existe
 * para paginas/acoes que genuinamente declararam uma restricao.
 *
 * <p>Aceita tanto o nome puro da role ({@code "ADMIN"}) quanto o padrao
 * {@code ROLE_} do Spring Security ({@code "ROLE_ADMIN"}) na
 * {@link GrantedAuthority} - a maioria dos projetos usa o prefixo
 * {@code ROLE_} por convencao do {@code hasRole(...)} do Spring Security,
 * mas {@code @RequiresRole("ADMIN")} no JTA nao deveria forcar o dev a
 * saber disso de antemao.
 *
 * <p><b>Cuidado corrigido (ver SECURITY.md, achado #3):</b>
 * {@code Authentication.isAuthenticated()} sozinho NAO distingue um
 * usuario de verdade de uma sessao anonima - o Spring Security
 * representa "ninguem logado" como um {@link AnonymousAuthenticationToken}
 * cujo {@code isAuthenticated()} retorna {@code true} por design (e um
 * "usuario anonimo autenticado como anonimo", nao a ausencia de
 * autenticacao). Excluir esse tipo explicitamente e o padrao correto e
 * bem conhecido do Spring Security para essa checagem - omiti-lo e um
 * erro comum o suficiente para ter nome proprio na comunidade.
 *
 * <p><b>Nao verificado neste ambiente</b> - depende de
 * {@code spring-security-core}, fora do acesso de rede disponivel aqui.
 */
final class JtaSecurityEnforcer {

    // OWASP A09:2021 (Security Logging and Monitoring Failures): antes desta
    // revisao, uma negacao de autorizacao (role errada, sem autenticacao)
    // nao deixava rastro nenhum - um atacante varrendo roles/selectors nao
    // gerava nenhum sinal pros logs. metadata.selector()/requiredRoles() sao
    // strings geradas pelo processor em compile-time (nunca vem do
    // atacante), entao seguras para logar sem sanitizacao.
    private static final Logger LOG = LoggerFactory.getLogger(JtaSecurityEnforcer.class);

    private JtaSecurityEnforcer() {
    }

    static boolean isAuthorized(ComponentMetadata metadata) {
        if (!metadata.isRestricted()) {
            return true;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            LOG.warn("Acesso negado a '{}' (requer role {}) - sem autenticacao valida",
                    metadata.selector(), metadata.requiredRoles());
            return false;
        }

        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String granted = authority.getAuthority();
            for (String required : metadata.requiredRoles()) {
                if (granted.equals(required) || granted.equals("ROLE_" + required)) {
                    return true;
                }
            }
        }
        LOG.warn("Acesso negado a '{}' (requer role {}) - usuario '{}' nao tem a role necessaria",
                metadata.selector(), metadata.requiredRoles(), authentication.getName());
        return false;
    }
}
