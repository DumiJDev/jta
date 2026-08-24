package dev.jta.spring;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registra dinamicamente um endpoint GET para cada componente com
 * {@code @Route}, apontando todos para um unico metodo despachante que
 * resolve o componente pelo <em>padrao</em> de rota que casou (nao pelo
 * path concreto da requisicao - ver abaixo).
 *
 * <p><b>Path variables:</b> como o Spring MVC ja fez o casamento de
 * padrao ({@code @Route("/produto/{id}")} contra {@code /produto/42})
 * antes de invocar {@link #dispatch}, nao precisamos reimplementar esse
 * casamento manualmente. Duas informacoes chegam via atributos de
 * requisicao que o proprio {@code RequestMappingHandlerMapping} ja
 * popula:
 * <ul>
 *   <li>{@link HandlerMapping#BEST_MATCHING_PATTERN_ATTRIBUTE} - o padrao
 *       original ({@code "/produto/{id}"}), usado para achar o
 *       {@link ComponentMetadata} certo em {@link #pathToComponent};</li>
 *   <li>{@link HandlerMapping#URI_TEMPLATE_VARIABLES_ATTRIBUTE} - o mapa
 *       {@code {id: "42"}} ja extraido, repassado para
 *       {@link JtaComponentInvoker#populateFromPathVariables}.</li>
 * </ul>
 *
 * <p>Query params tambem sao populados no GET inicial (nao so em acoes
 * HTMX subsequentes) - sem isso, um link com {@code ?pagina=2} nunca
 * chegaria ao componente no carregamento da pagina.
 *
 * <p><b>Composicao de layout:</b> se a pagina declarou
 * {@code @Route(layout = X.class)}, a pagina e renderizada primeiro (como
 * sempre), e o HTML resultante e passado como parametro {@code content}
 * para o template do layout - que tem DOIS {@code @param} (self do
 * proprio layout, e content). Templates JTE com mais de um {@code @param}
 * sao renderizados via {@code render(nome, Map, output)} (a variante
 * "renderMap" que o {@code jte-maven-plugin} gera), nao a variante
 * posicional de parametro unico usada para paginas sem layout. Este e o
 * unico ponto desta classe que NAO foi validado contra o JTE de verdade
 * neste ambiente (sem acesso ao Maven Central) - risco tecnico assumido
 * conscientemente, documentado em TROUBLESHOOTING.md.
 *
 * <p>Esta e a peca do MVP com maior risco tecnico (uso de API interna do
 * Spring MVC para registro dinamico de rotas) - se
 * {@code RequestMappingHandlerMapping.registerMapping} nao estiver
 * disponivel no classpath do projeto consumidor por algum motivo, o
 * fallback documentado e trocar esta classe por um unico
 * {@code @RequestMapping("/**")} que despacha manualmente.
 *
 * <p>Registrado como bean explicitamente por {@link JtaAutoConfiguration}
 * (nao via {@code @Component}), ja que este pacote fica fora do
 * component-scan padrao do app consumidor.
 */
class JtaRouteRegistrar implements InitializingBean {

    private final ComponentRegistry registry;
    private final JtaComponentInvoker invoker;
    private final TemplateEngine templateEngine;
    private final JtaConfig config;
    private final RequestMappingHandlerMapping handlerMapping;

    private final Map<String, ComponentMetadata> pathToComponent = new HashMap<>();

    JtaRouteRegistrar(ComponentRegistry registry, JtaComponentInvoker invoker, TemplateEngine templateEngine,
                       JtaConfig config, RequestMappingHandlerMapping handlerMapping) {
        this.registry = registry;
        this.invoker = invoker;
        this.templateEngine = templateEngine;
        this.config = config;
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        Method dispatchMethod = JtaRouteRegistrar.class.getDeclaredMethod("dispatch", HttpServletRequest.class);

        for (ComponentMetadata page : registry.pages()) {
            pathToComponent.put(page.routePath(), page);

            RequestMappingInfo mappingInfo = RequestMappingInfo
                    .paths(page.routePath())
                    .methods(RequestMethod.GET)
                    .build();
            handlerMapping.registerMapping(mappingInfo, this, dispatchMethod);
        }
    }

    @SuppressWarnings("unused") // invocado via reflection pelo RequestMappingHandlerMapping
    ResponseEntity<String> dispatch(HttpServletRequest request) {
        String matchedPattern = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        ComponentMetadata metadata = matchedPattern != null ? pathToComponent.get(matchedPattern) : null;
        if (metadata == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (!JtaSecurityEnforcer.isAuthorized(metadata)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
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
        java.util.Set<String> bindableFields = java.util.Set.copyOf(metadata.bindableFields());
        invoker.populateFromParams(instance, request.getParameterMap(), bindableFields);

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        invoker.populateFromPathVariables(instance, pathVariables, bindableFields);
        invoker.callInitIfPresent(instance);

        StringOutput pageOutput = new StringOutput();
        templateEngine.render(metadata.generatedJteTemplate(), instance, pageOutput);
        String pageHtml = pageOutput.toString();

        String bodyHtml = metadata.hasLayout() ? renderWithLayout(metadata, pageHtml) : pageHtml;

        String fullPage = PageShellRenderer.wrap(bodyHtml, registry, config);
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(fullPage);
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

        StringOutput layoutOutput = new StringOutput();
        templateEngine.render(layoutMetadata.generatedJteTemplate(), params, layoutOutput);
        return layoutOutput.toString();
    }
}
