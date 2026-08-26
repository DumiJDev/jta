package dev.jta.runtime;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Nucleo agnostico de framework do suporte a {@code @Sse}: rastreia
 * conexoes por path, agenda o re-render periodico de cada componente
 * {@code @Sse} (ver limitacao documentada em {@link dev.jta.core.Sse} -
 * isto e polling por intervalo, nao push orientado a evento) e transmite o
 * HTML resultante para todo {@link Subscriber} inscrito naquele path.
 *
 * <p>Extraido de {@code JtaSseController} (jta-spring-boot-starter, unico
 * adaptador que tinha SSE ate agora) - a unica mudanca de comportamento e
 * que o transporte (como o evento chega ao cliente - {@code SseEmitter} do
 * Spring MVC, {@code SseClient} do Javalin, escrita direta num
 * {@code HttpExchange} no standalone, ou {@code HttpServerResponse} do
 * Vert.x no Quarkus) e responsabilidade do adaptador via {@link Subscriber},
 * nao desta classe. Tudo que e verdadeiramente agnostico - registro de
 * conexao, checagem de {@code @RequiresRole} via {@link SecurityEnforcer},
 * o agendador e a logica de broadcast - vive aqui, para nao ser duplicado
 * em cada um dos 4 adaptadores.
 */
public final class SseHub {

    private static final Logger LOG = LoggerFactory.getLogger(SseHub.class);

    /**
     * Ponto de entrega de um evento SSE a um cliente conectado - a parte
     * especifica de transporte que cada adaptador implementa (ex:
     * {@code SseEmitter::send} no Spring, {@code SseClient::sendEvent} no
     * Javalin, escrita crua num {@code OutputStream} no standalone).
     *
     * <p>Uma excecao lancada daqui e interpretada por {@link #broadcast}
     * como "cliente desconectado" - o subscriber e removido
     * automaticamente, sem derrubar o broadcast para os demais.
     */
    @FunctionalInterface
    public interface Subscriber {
        void onEvent(String data) throws Exception;
    }

    private final ComponentRegistry registry;
    private final ComponentInvoker invoker;
    private final TemplateEngine templateEngine;
    private final ClassLoader classLoader;

    private final Map<String, List<Subscriber>> subscribersByPath = new ConcurrentHashMap<>();
    private final Map<String, ComponentMetadata> metadataByPath = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    public SseHub(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine) {
        this.registry = registry;
        this.invoker = invoker;
        this.templateEngine = templateEngine;
        // Capturado aqui (construcao do hub, tipicamente dentro de um CDI
        // producer/bean de inicializacao do adaptador - o mesmo momento em
        // que ComponentRegistry.loadFromClasspath ja usa este classloader
        // com sucesso), nao lido de novo dentro de broadcast(): broadcast
        // roda no thread proprio do ScheduledExecutorService criado por
        // start() (nao numa thread de request), e Class.forName(fqn) de 1
        // argumento resolve pelo classloader que definiu SseHub, nao pelo
        // contexto da thread atual - sob o classloading em varias camadas
        // do Quarkus (QuarkusClassLoader), essa thread propria acaba sem
        // visibilidade das classes da aplicacao (ClassNotFoundException bem
        // real, encontrado rodando o TCK do Quarkus pela primeira vez -
        // toda tick do agendador falhava silenciosamente). Nos outros 3
        // adaptadores (classloader unico, convencional) isto e um no-op.
        this.classLoader = Thread.currentThread().getContextClassLoader();
    }

    /** Componentes {@code @Sse} conhecidos pelo {@link ComponentRegistry} associado a este hub. */
    public List<ComponentMetadata> sseComponents() {
        return registry.all().stream().filter(ComponentMetadata::hasSse).toList();
    }

    /**
     * Registra cada componente {@code @Sse} e agenda seu broadcast
     * periodico. Idempotente-por-instancia: chamar mais de uma vez sobre
     * o mesmo hub reagenda tudo de novo (o chamador deve chamar isto uma
     * unica vez, tipicamente na inicializacao do adaptador). No-op se nao
     * houver componente {@code @Sse} nenhum.
     */
    public synchronized void start() {
        List<ComponentMetadata> sseComponents = sseComponents();
        if (sseComponents.isEmpty()) {
            return;
        }

        scheduler = Executors.newScheduledThreadPool(Math.max(1, sseComponents.size()));
        for (ComponentMetadata metadata : sseComponents) {
            subscribersByPath.put(metadata.ssePath(), new CopyOnWriteArrayList<>());
            metadataByPath.put(metadata.ssePath(), metadata);
            scheduler.scheduleAtFixedRate(() -> broadcast(metadata),
                    metadata.sseIntervalMillis(), metadata.sseIntervalMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /** Encerra o agendador. Seguro de chamar mesmo se {@link #start()} nunca rodou ou foi um no-op. */
    public synchronized void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /** Metadados do componente {@code @Sse} registrado naquele path, ou {@code null} se nao existir. */
    public ComponentMetadata metadataFor(String path) {
        return metadataByPath.get(path);
    }

    /**
     * Autoriza uma conexao contra {@code @RequiresRole} (ver
     * {@link SecurityEnforcer} - mesma checagem usada por
     * {@link JtaPageDispatcher}/{@link JtaActionDispatcher}, agora tambem
     * aplicada aqui: ver SECURITY.md, achado #4, "@Sse nunca checava
     * @RequiresRole"). {@code false} tambem para um path sem componente
     * {@code @Sse} registrado.
     */
    public boolean isAuthorized(String path, CurrentUser user) {
        ComponentMetadata metadata = metadataByPath.get(path);
        return metadata != null && SecurityEnforcer.isAuthorized(metadata, user);
    }

    /**
     * Inscreve um {@link Subscriber} para receber o HTML re-renderizado
     * daquele path a cada tick do agendador. O chamador deve ter
     * verificado {@link #isAuthorized} antes de chamar isto.
     *
     * @throws IllegalArgumentException se o path nao corresponder a um componente {@code @Sse} conhecido
     */
    public void subscribe(String path, Subscriber subscriber) {
        List<Subscriber> subscribers = subscribersByPath.get(path);
        if (subscribers == null) {
            throw new IllegalArgumentException("Nenhum componente @Sse registrado em '" + path + "'");
        }
        subscribers.add(subscriber);
    }

    /** Remove a inscricao - o adaptador chama isto quando detecta que o cliente desconectou. No-op se ja removido. */
    public void unsubscribe(String path, Subscriber subscriber) {
        List<Subscriber> subscribers = subscribersByPath.get(path);
        if (subscribers != null) {
            subscribers.remove(subscriber);
        }
    }

    private void broadcast(ComponentMetadata metadata) {
        List<Subscriber> subscribers = subscribersByPath.get(metadata.ssePath());
        if (subscribers == null || subscribers.isEmpty()) {
            return; // ninguem conectado - nao vale a pena renderizar
        }

        String html;
        try {
            Class<?> type = Class.forName(metadata.fqn(), true, classLoader);
            Object instance = invoker.instantiate(type);
            invoker.callInitIfPresent(instance);
            StringOutput output = new StringOutput();
            // Mesmo contrato de render de JtaPageDispatcher/JtaActionDispatcher:
            // o Map nomeado, nao o overload de modelo unico do JTE. O template
            // gerado declara hoje DOIS parametros ("self" e "__jtaInvoker", este
            // ultimo usado pela composicao de componentes @Use/@Input), e o
            // overload de modelo unico so sabe passar um - dava
            // "IllegalArgumentException: wrong number of arguments: 3 expected: 4"
            // em todo tick do agendador, engolido pelo catch abaixo (so um WARN).
            // Como o JTE resolve os parametros do Map por nome e ignora chaves
            // que o template nao declara, isto funciona igual para um template
            // sem "__jtaInvoker".
            Map<String, Object> renderParams = new LinkedHashMap<>();
            renderParams.put("self", instance);
            renderParams.put("__jtaInvoker", invoker);
            templateEngine.render(metadata.generatedJteTemplate(), renderParams, output);
            html = output.toString();
        } catch (Exception e) {
            LOG.warn("Falha ao re-renderizar componente @Sse '{}' - tick ignorado", metadata.selector(), e);
            return; // um erro de render nao deve derrubar o agendador inteiro
        }

        for (Subscriber subscriber : List.copyOf(subscribers)) {
            try {
                subscriber.onEvent(html);
            } catch (Exception e) {
                subscribers.remove(subscriber);
            }
        }
    }
}
