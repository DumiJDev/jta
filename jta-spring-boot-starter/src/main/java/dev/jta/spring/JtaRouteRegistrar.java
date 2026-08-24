package dev.jta.spring;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.runtime.JtaPageDispatcher;
import dev.jta.runtime.PageResult;
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
import java.util.Map;

/**
 * Registra dinamicamente um endpoint GET para cada componente com
 * {@code @Route}, apontando todos para um unico metodo despachante que
 * resolve o componente pelo <em>padrao</em> de rota que casou (nao pelo
 * path concreto da requisicao - ver abaixo).
 *
 * <p><b>Adaptador fino:</b> o registro de rota via
 * {@code RequestMappingHandlerMapping} e a extracao de path
 * variables/query params da {@link HttpServletRequest} sao inerentemente
 * especificos do Spring MVC (cada framework web resolve isso do seu
 * jeito) - mas tudo que acontece depois de resolver o
 * {@link ComponentMetadata} (autorizacao, reidratacao de estado, render,
 * composicao de layout) esta em {@link JtaPageDispatcher} (jta-runtime,
 * agnostico de framework).
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
 *       {@code {id: "42"}} ja extraido, repassado ao
 *       {@link JtaPageDispatcher}.</li>
 * </ul>
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
    private final JtaPageDispatcher dispatcher;
    private final RequestMappingHandlerMapping handlerMapping;

    private final Map<String, ComponentMetadata> pathToComponent = new HashMap<>();

    JtaRouteRegistrar(ComponentRegistry registry, JtaPageDispatcher dispatcher, RequestMappingHandlerMapping handlerMapping) {
        this.registry = registry;
        this.dispatcher = dispatcher;
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

        @SuppressWarnings("unchecked")
        Map<String, String> pathVariables =
                (Map<String, String>) request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        PageResult result = dispatcher.dispatch(metadata, request.getParameterMap(), pathVariables, SpringCurrentUser.current());

        if (result instanceof PageResult.Forbidden) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        PageResult.Rendered rendered = (PageResult.Rendered) result;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(rendered.html());
    }
}
