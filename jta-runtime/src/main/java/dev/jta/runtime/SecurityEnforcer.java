package dev.jta.runtime;

import dev.jta.core.ComponentMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verifica {@code @RequiresRole} contra o {@link CurrentUser} da
 * requisicao atual. Um componente sem {@code @RequiresRole} (a maioria)
 * nunca passa por aqui de verdade - {@link ComponentMetadata#isRestricted()}
 * e checado primeiro pelos chamadores ({@link JtaPageDispatcher},
 * {@link JtaActionDispatcher}), entao o custo desta classe so existe para
 * paginas/acoes que genuinamente declararam uma restricao.
 *
 * <p>Aceita tanto o nome puro da role ({@code "ADMIN"}) quanto o padrao
 * {@code ROLE_} do Spring Security ({@code "ROLE_ADMIN"}) em
 * {@link CurrentUser#authorities()} - a maioria dos projetos Spring usa o
 * prefixo {@code ROLE_} por convencao do {@code hasRole(...)}, mas
 * {@code @RequiresRole("ADMIN")} no JTA nao deveria forcar o dev a saber
 * disso de antemao. Frameworks sem esse prefixo (ex: um adaptador
 * standalone que so usa nomes puros) tambem funcionam sem alteracao.
 *
 * <p>Extraido de {@code JtaSecurityEnforcer} (jta-spring-boot-starter) na
 * extracao do nucleo agnostico - unica mudanca de comportamento e receber
 * {@link CurrentUser} em vez de ler {@code SecurityContextHolder}
 * diretamente; a logica de match de role e o log de negacao (OWASP
 * A09:2021, ver SECURITY.md achado #10) sao identicos.
 */
public final class SecurityEnforcer {

    private static final Logger LOG = LoggerFactory.getLogger(SecurityEnforcer.class);

    private SecurityEnforcer() {
    }

    public static boolean isAuthorized(ComponentMetadata metadata, CurrentUser user) {
        if (!metadata.isRestricted()) {
            return true;
        }

        if (!user.isAuthenticated()) {
            LOG.warn("Acesso negado a '{}' (requer role {}) - sem autenticacao valida",
                    metadata.selector(), metadata.requiredRoles());
            return false;
        }

        for (String granted : user.authorities()) {
            for (String required : metadata.requiredRoles()) {
                if (granted.equals(required) || granted.equals("ROLE_" + required)) {
                    return true;
                }
            }
        }
        LOG.warn("Acesso negado a '{}' (requer role {}) - usuario nao tem a role necessaria",
                metadata.selector(), metadata.requiredRoles());
        return false;
    }
}
