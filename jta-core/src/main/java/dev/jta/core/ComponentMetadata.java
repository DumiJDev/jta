package dev.jta.core;

import java.util.List;
import java.util.Map;

/**
 * Metadados de um componente, emitidos pelo {@code JtaAnnotationProcessor}
 * em compile-time e serializados em {@code META-INF/jta/components.json}
 * dentro do jar. E o unico artefato que a camada de runtime (starters)
 * precisa ler para saber quais componentes existem, quais rotas registrar
 * e para qual classe despachar cada acao HTMX.
 *
 * @param fqn             nome totalmente qualificado da classe do componente
 * @param selector        seletor canonico (derivado do FQN) ou explicito, ja resolvido e unico
 * @param explicitSelector true se o selector veio de {@code @AComponent(selector=...)}
 * @param routePath       caminho de {@code @Route}, ou {@code null} se o componente nao e uma pagina
 * @param actions         nomes dos metodos de acao (void) declarados no componente
 * @param generatedJteTemplate caminho do template .jte gerado, relativo a raiz de templates JTE
 * @param scopedCss       CSS de {@code @AComponent(style=...)} ja prefixado com
 *                        {@code [data-jta-component="<selector>"]} em cada regra
 *                        top-level (ver {@code CssScoper} no processor), ou
 *                        {@code null}/vazio se o componente nao declarou style.
 *                        Consumido pela camada de runtime para montar o
 *                        {@code <style>} da pagina.
 * @param isLayout        true se este componente foi anotado com {@code @Layout}
 *                        (ao inves de {@code @AComponent}) - o template gerado
 *                        tem dois {@code @param} (self e content), nao um
 * @param layoutFqn       FQN do {@code @Layout} declarado via
 *                        {@code @Route(layout = ...)}, ou {@code null} se a
 *                        pagina nao usa layout nenhum
 * @param requiredRoles   roles de {@code @RequiresRole}, vazio se o componente
 *                        nao declarou nenhuma restricao
 * @param allowAnonymous  true se o componente foi anotado com {@code @AllowAnonymous}
 * @param ssePath         caminho de {@code @Sse}, ou {@code null} se o componente
 *                        nao expoe um endpoint SSE
 * @param sseIntervalMillis intervalo de re-render de {@code @Sse}, em milissegundos
 * @param bindableFields  campos publicos que podem ser populados a partir de
 *                        query params/path variables/form data - por padrao,
 *                        so os campos que o template referencia via {{ }} mais
 *                        os declarados como {param} de rota, mais qualquer
 *                        campo anotado {@code @Bindable} explicitamente.
 *                        "Seguro por padrao": um campo publico que o template
 *                        nunca menciona NAO e mais bindavel so por ser publico
 *                        (ver SECURITY.md, achado #5 - mass assignment).
 * @param actionParams    nome da acao -> tipos simples ordenados dos seus
 *                        parametros (ex: {@code ["String"]} para
 *                        {@code remover(String id)}). Ausente/vazio para uma
 *                        acao = aridade 0, compativel com componentes ja
 *                        compilados antes desta feature (sempre tinham
 *                        aridade 0). Consumido pelas duas camadas de defesa
 *                        contra invocacao de acao com aridade incompativel
 *                        (ver {@code JtaActionDispatcher}/{@code ComponentInvoker}).
 * @param inputs          nomes dos campos publicos anotados {@code @Input}
 *                        deste componente - informativo/tooling; a validacao
 *                        real de property binding pai->filho acontece em
 *                        compile-time no modulo do PAI (que enxerga o filho
 *                        via {@link javax.lang.model.element.TypeElement},
 *                        nao precisa reler isto de {@code components.json}).
 * @param children        FQNs (deduplicados) dos componentes aninhados
 *                        diretamente no template deste componente - base
 *                        para a deteccao de ciclo de aninhamento (DFS sobre
 *                        a aresta fqn -> children, rodada uma vez ao final
 *                        do processing do modulo).
 */
public record ComponentMetadata(
        String fqn,
        String selector,
        boolean explicitSelector,
        String routePath,
        List<String> actions,
        String generatedJteTemplate,
        String scopedCss,
        boolean isLayout,
        String layoutFqn,
        List<String> requiredRoles,
        boolean allowAnonymous,
        String ssePath,
        long sseIntervalMillis,
        List<String> bindableFields,
        Map<String, List<String>> actionParams,
        List<String> inputs,
        List<String> children
) {
    public boolean isPage() {
        return routePath != null && !routePath.isBlank();
    }

    public boolean hasStyle() {
        return scopedCss != null && !scopedCss.isBlank();
    }

    public boolean hasLayout() {
        return layoutFqn != null && !layoutFqn.isBlank();
    }

    public boolean isRestricted() {
        return requiredRoles != null && !requiredRoles.isEmpty();
    }

    public boolean hasSse() {
        return ssePath != null && !ssePath.isBlank();
    }

    /**
     * Aridade declarada de uma acao - 0 se a acao nao aparecer em
     * {@link #actionParams()} (compatibilidade com metadata antiga, que
     * sempre tratava acoes como aridade 0).
     */
    public int actionArity(String action) {
        List<String> params = actionParams == null ? null : actionParams.get(action);
        return params == null ? 0 : params.size();
    }
}
