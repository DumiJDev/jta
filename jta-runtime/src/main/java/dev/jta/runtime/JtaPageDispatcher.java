package dev.jta.runtime;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import dev.jta.runtime.csrf.CsrfToken;
import dev.jta.runtime.csrf.CsrfTokenStore;
import dev.jta.runtime.session.JtaSession;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Orquestra a renderizacao de uma pagina (componente com {@code @Route}),
 * agnostico de framework web: autoriza, reidrata o estado a partir de
 * query params + path variables (mais a sessao resolvida pelo adaptador),
 * chama {@code init()} se declarado, renderiza (compondo com
 * {@code @Layout} quando presente), emite/renova o token CSRF e envolve no
 * documento HTML completo via {@code PageShellRenderer}.
 *
 * <p>Casamento de padrao de rota (qual {@link ComponentMetadata} responde
 * por qual URL) e inerentemente especifico de cada framework web (Spring
 * MVC, Javalin, etc. resolvem isso cada um do seu jeito) - por isso este
 * dispatcher recebe o {@link ComponentMetadata} ja resolvido pelo
 * adaptador, e so cuida do que acontece depois disso. Ver
 * {@code JtaRouteRegistrar} em jta-spring-boot-starter para o adaptador
 * Spring MVC completo (registro de rota + extracao de path
 * variables/query params da {@code HttpServletRequest}).
 *
 * <p><b>CSRF (ver SECURITY.md, achado #6):</b> GET e seguro por definicao,
 * entao esta classe nunca verifica CSRF - so emite/renova o token via
 * {@link CsrfTokenStore#issue}, para o {@code PageShellRenderer} embutir no
 * {@code hx-headers} do {@code <body>} e o adaptador aplicar o
 * {@code Set-Cookie} resultante (ver {@code PageResult.Rendered}).
 *
 * <p>Extraido de {@code JtaRouteRegistrar} (jta-spring-boot-starter) na
 * extracao do nucleo agnostico - mesma sequencia de passos, incluindo a
 * composicao de layout em runtime (secao correspondente do documento de
 * arquitetura).
 */
public final class JtaPageDispatcher {

    private final ComponentRegistry registry;
    private final ComponentInvoker invoker;
    private final TemplateEngine templateEngine;
    private final JtaConfig config;
    private final CsrfTokenStore csrfTokenStore;

    public JtaPageDispatcher(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                              JtaConfig config, CsrfTokenStore csrfTokenStore) {
        this.registry = registry;
        this.invoker = invoker;
        this.templateEngine = templateEngine;
        this.config = config;
        this.csrfTokenStore = csrfTokenStore;
    }

    public PageResult dispatch(ComponentMetadata metadata, Map<String, String[]> queryParams,
                                Map<String, String> pathVariables, CurrentUser user, JtaSession session,
                                String cookieHeader) {
        if (!SecurityEnforcer.isAuthorized(metadata, user)) {
            return new PageResult.Forbidden();
        }

        Object instance;
        try {
            Class<?> type = Class.forName(metadata.fqn());
            instance = invoker.instantiate(type);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Classe do componente nao encontrada: " + metadata.fqn(), e);
        }

        // query params primeiro, path variables por cima (o path e mais
        // especifico que a query string quando os dois definem o mesmo
        // campo, ja que o path e obrigatorio para a rota ter casado).
        Set<String> bindableFields = Set.copyOf(metadata.bindableFields());
        invoker.populateFromParams(instance, queryParams, bindableFields);
        invoker.populateFromPathVariables(instance, pathVariables, bindableFields);
        invoker.applySession(instance, session);
        invoker.callInitIfPresent(instance);

        StringOutput pageOutput = new StringOutput();
        Map<String, Object> renderParams = new LinkedHashMap<>();
        renderParams.put("self", instance);
        renderParams.put("__jtaInvoker", invoker);
        templateEngine.render(metadata.generatedJteTemplate(), renderParams, pageOutput);
        String pageHtml = pageOutput.toString();

        String bodyHtml = metadata.hasLayout() ? renderWithLayout(metadata, pageHtml) : pageHtml;

        CsrfTokenStore.IssueResult issued = csrfTokenStore.issue(cookieHeader);
        CsrfToken csrfToken = issued.tokenValue() == null ? null
                : new CsrfToken(csrfTokenStore.headerName(), issued.tokenValue());

        String fullPage = PageShellRenderer.wrap(bodyHtml, registry, config, csrfToken);
        return new PageResult.Rendered(fullPage, issued.setCookieHeaderOrNull());
    }

    /**
     * Renderiza o layout declarado em {@code @Route(layout = ...)},
     * passando o HTML ja renderizado da pagina como o parametro
     * {@code content}. O template gerado do layout tem dois
     * {@code @param} (self, content), entao precisa do render por
     * {@code Map} em vez do positional de parametro unico.
     */
    private String renderWithLayout(ComponentMetadata pageMetadata, String pageHtml) {
        ComponentMetadata layoutMetadata = registry.byFqn(pageMetadata.layoutFqn());

        Object layoutInstance;
        try {
            layoutInstance = invoker.instantiate(Class.forName(layoutMetadata.fqn()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Classe do layout nao encontrada: " + layoutMetadata.fqn(), e);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("self", layoutInstance);
        params.put("content", pageHtml);
        params.put("__jtaInvoker", invoker);

        StringOutput layoutOutput = new StringOutput();
        templateEngine.render(layoutMetadata.generatedJteTemplate(), params, layoutOutput);
        return layoutOutput.toString();
    }
}
