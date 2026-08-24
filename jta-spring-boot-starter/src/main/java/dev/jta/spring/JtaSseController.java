package dev.jta.spring;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Registra um endpoint SSE para cada componente anotado com {@code @Sse},
 * re-renderizando periodicamente (nao orientado a evento - ver limitacao
 * documentada em {@link dev.jta.core.Sse}) e transmitindo o HTML para
 * todos os clientes conectados naquele path.
 *
 * <p>Cada instancia re-renderizada e nova e sem estado de requisicao
 * (nao ha "requisicao atual" entre ticks do agendador) - o componente
 * usado num {@code @Sse} deve ser autossuficiente (ex: ler de um servico
 * injetado via construtor), nao depender de query params/path variables.
 *
 * <p><b>Nao verificado neste ambiente</b> - depende de
 * {@code SseEmitter} (Spring MVC) e de rodar de verdade sob um servidor
 * servlet, fora do que da para testar sem Maven Central.
 */
class JtaSseController implements InitializingBean, DisposableBean {

    private final ComponentRegistry registry;
    private final JtaComponentInvoker invoker;
    private final TemplateEngine templateEngine;
    private final RequestMappingHandlerMapping handlerMapping;

    private final Map<String, List<SseEmitter>> emittersByPath = new ConcurrentHashMap<>();
    private final Map<String, ComponentMetadata> metadataByPath = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    JtaSseController(ComponentRegistry registry, JtaComponentInvoker invoker, TemplateEngine templateEngine,
                      RequestMappingHandlerMapping handlerMapping) {
        this.registry = registry;
        this.invoker = invoker;
        this.templateEngine = templateEngine;
        this.handlerMapping = handlerMapping;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        List<ComponentMetadata> sseComponents = registry.all().stream().filter(ComponentMetadata::hasSse).toList();
        if (sseComponents.isEmpty()) {
            return;
        }

        scheduler = Executors.newScheduledThreadPool(Math.max(1, sseComponents.size()));
        Method connectMethod = JtaSseController.class.getDeclaredMethod("connect", HttpServletRequest.class);

        for (ComponentMetadata metadata : sseComponents) {
            emittersByPath.put(metadata.ssePath(), new CopyOnWriteArrayList<>());
            metadataByPath.put(metadata.ssePath(), metadata);

            RequestMappingInfo mappingInfo = RequestMappingInfo
                    .paths(metadata.ssePath())
                    .methods(RequestMethod.GET)
                    .build();
            handlerMapping.registerMapping(mappingInfo, this, connectMethod);

            scheduler.scheduleAtFixedRate(() -> broadcast(metadata),
                    metadata.sseIntervalMillis(), metadata.sseIntervalMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * <b>Vulnerabilidade real corrigida (ver SECURITY.md, achado #4):</b>
     * esta classe nunca checava {@code @RequiresRole} - um componente que
     * combinasse {@code @Sse} com {@code @RequiresRole} transmitia para
     * qualquer um que conectasse, ignorando a restricao (que ja era
     * respeitada corretamente em {@code JtaRouteRegistrar} e
     * {@code JtaActionController}, os outros dois pontos de entrada).
     */
    @SuppressWarnings("unused") // invocado via reflection pelo RequestMappingHandlerMapping
    SseEmitter connect(HttpServletRequest request) {
        String path = (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
        ComponentMetadata metadata = metadataByPath.get(path);
        if (metadata == null || !JtaSecurityEnforcer.isAuthorized(metadata)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.FORBIDDEN);
        }

        List<SseEmitter> emitters = emittersByPath.get(path);
        SseEmitter emitter = new SseEmitter(0L); // sem timeout - a conexao fica aberta ate o cliente fechar
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        return emitter;
    }

    private void broadcast(ComponentMetadata metadata) {
        List<SseEmitter> emitters = emittersByPath.get(metadata.ssePath());
        if (emitters == null || emitters.isEmpty()) {
            return; // ninguem conectado - nao vale a pena renderizar
        }

        String html;
        try {
            Class<?> type = Class.forName(metadata.fqn());
            Object instance = invoker.instantiate(type);
            StringOutput output = new StringOutput();
            templateEngine.render(metadata.generatedJteTemplate(), instance, output);
            html = output.toString();
        } catch (Exception e) {
            return; // um erro de render nao deve derrubar o agendador inteiro
        }

        for (SseEmitter emitter : List.copyOf(emitters)) {
            try {
                emitter.send(SseEmitter.event().data(html));
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
}
