package dev.jta.spring;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.runtime.ComponentInvoker;
import dev.jta.runtime.SseHub;
import gg.jte.TemplateEngine;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Registra um endpoint SSE para cada componente anotado com {@code @Sse} -
 * adaptador fino do Spring MVC ({@link SseEmitter}) sobre {@link SseHub}
 * (jta-runtime, agnostico de framework), que faz todo o trabalho real:
 * rastreio de conexao, checagem de {@code @RequiresRole}, agendamento do
 * re-render periodico (ver limitacao documentada em
 * {@link dev.jta.core.Sse}) e broadcast do HTML resultante.
 *
 * <p>Extraido para {@link SseHub} para os outros 3 adaptadores (Javalin,
 * standalone, Quarkus) reusarem a mesma logica - antes desta extracao, SSE
 * so existia aqui.
 *
 * <p><b>Verificado</b> por {@code SpringJtaTckTest} (jta-tck), que sobe um
 * servidor servlet real e le a primeira linha {@code data:} de um endpoint
 * {@code @Sse} - a nota anterior de "nao verificado neste ambiente" caiu
 * quando o TCK passou a cobrir SSE nos 4 adaptadores.
 */
class JtaSseController implements InitializingBean, DisposableBean {

    private final SseHub hub;
    private final RequestMappingHandlerMapping handlerMapping;

    JtaSseController(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                      RequestMappingHandlerMapping handlerMapping) {
        this.hub = new SseHub(registry, invoker, templateEngine);
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        List<ComponentMetadata> sseComponents = hub.sseComponents();
        if (sseComponents.isEmpty()) {
            return;
        }

        hub.start();
        Method connectMethod = JtaSseController.class.getDeclaredMethod("connect", HttpServletRequest.class);
        for (ComponentMetadata metadata : sseComponents) {
            RequestMappingInfo mappingInfo = RequestMappingInfo
                    .paths(metadata.ssePath())
                    .methods(RequestMethod.GET)
                    .build();
            handlerMapping.registerMapping(mappingInfo, this, connectMethod);
        }
    }

    /**
     * <b>Vulnerabilidade real corrigida (ver SECURITY.md, achado #4):</b>
     * esta classe nunca checava {@code @RequiresRole} - um componente que
     * combinasse {@code @Sse} com {@code @RequiresRole} transmitia para
     * qualquer um que conectasse, ignorando a restricao (que ja era
     * respeitada corretamente em {@code JtaRouteRegistrar} e
     * {@code JtaActionController}, os outros dois pontos de entrada). A
     * checagem agora vive em {@link SseHub#isAuthorized}, compartilhada
     * por todo adaptador.
     */
    @SuppressWarnings("unused") // invocado via reflection pelo RequestMappingHandlerMapping
    SseEmitter connect(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        if (!hub.isAuthorized(path, SpringCurrentUser.current())) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }

        SseEmitter emitter = new SseEmitter(0L); // sem timeout - a conexao fica aberta ate o cliente fechar
        SseHub.Subscriber subscriber = data -> emitter.send(SseEmitter.event().data(data));
        hub.subscribe(path, subscriber);
        emitter.onCompletion(() -> hub.unsubscribe(path, subscriber));
        emitter.onTimeout(() -> hub.unsubscribe(path, subscriber));
        emitter.onError(e -> hub.unsubscribe(path, subscriber));
        return emitter;
    }

    @Override
    public void destroy() {
        hub.stop();
    }
}
