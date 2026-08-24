package dev.jta.demo;

import dev.jta.core.AComponent;

/**
 * Componente dedicado exclusivamente ao teste de regressao de seguranca
 * em {@code SecurityRegressionTest} (ver SECURITY.md, achado #5 - mass
 * assignment). Sem {@code @Route}: nao e uma pagina, so existe para ter
 * um selector/acao alcancavel via {@code /__jta/action/...}.
 *
 * <p>{@code nome} e interpolado diretamente no template ({@code {{ nome }}}),
 * entao entra em {@code bindableFields}. {@code isAdmin} nunca e
 * interpolado diretamente - so e lido de dentro de
 * {@link #isAdminComoTexto()}, o que NAO conta para {@code bindableFields}
 * (essa e exatamente a distincao que o achado #5 corrigiu) - por isso deve
 * continuar {@code false} mesmo que a requisicao envie {@code isAdmin=true}.
 */
@AComponent(
    template = "<div>{{ nome }}</div><div>{{ isAdminComoTexto() }}</div>"
)
public class SecurityProbe {

    public String nome = "";
    public boolean isAdmin = false;

    public String isAdminComoTexto() {
        return String.valueOf(isAdmin);
    }

    public void tocar() {
        // acao no-op - so para poder disparar populateFromParams via POST
    }
}
