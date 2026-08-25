package dev.jta.quarkus;

import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import dev.jta.runtime.ComponentInvoker;
import dev.jta.runtime.JtaActionDispatcher;
import dev.jta.runtime.JtaPageDispatcher;
import dev.jta.runtime.SseHub;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import jakarta.validation.Validator;

/**
 * Produtores CDI dos beans centrais do JTA - mesmo papel de
 * {@code JtaAutoConfiguration} no starter Spring, so que via producer
 * methods em vez de {@code @Bean}. Registrado explicitamente pelo
 * {@code AdditionalBeanBuildItem} do modulo deployment, ja que o jar
 * runtime desta extensao nao faz parte do bean archive index da app por
 * default.
 *
 * <p><b>Importante:</b> {@link TemplateEngine#createPrecompiled} carrega
 * classes {@code .jte} ja compiladas via classloader - o consumidor
 * precisa configurar o {@code jte-maven-plugin} (goal {@code precompile})
 * no seu proprio build, exatamente como no starter Spring.
 */
@Singleton
class JtaCdiProducers {

    @Produces
    @ApplicationScoped
    ComponentRegistry componentRegistry() {
        return ComponentRegistry.loadFromClasspath(Thread.currentThread().getContextClassLoader());
    }

    @Produces
    @ApplicationScoped
    JtaConfig jtaConfig() {
        return JtaConfig.loadFromClasspath(Thread.currentThread().getContextClassLoader());
    }

    @Produces
    @ApplicationScoped
    TemplateEngine templateEngine() {
        return TemplateEngine.createPrecompiled(ContentType.Html);
    }

    @Produces
    @ApplicationScoped
    ComponentInvoker componentInvoker() {
        return new ComponentInvoker(new QuarkusComponentFactory());
    }

    @Produces
    @ApplicationScoped
    JtaPageDispatcher jtaPageDispatcher(ComponentRegistry registry, ComponentInvoker invoker,
                                         TemplateEngine templateEngine, JtaConfig config) {
        return new JtaPageDispatcher(registry, invoker, templateEngine, config);
    }

    @Produces
    @ApplicationScoped
    JtaActionDispatcher jtaActionDispatcher(ComponentRegistry registry, ComponentInvoker invoker,
                                             TemplateEngine templateEngine, Instance<Validator> validator) {
        // Validator so existe se o consumidor tiver quarkus-hibernate-validator
        // no classpath - Instance<> deixa isso opcional, igual ao
        // ObjectProvider<Validator> do starter Spring.
        return new JtaActionDispatcher(registry, invoker, templateEngine,
                validator.isResolvable() ? validator.get() : null);
    }

    /**
     * {@link SseHub} compartilhado por toda conexao {@code @Sse} - criado
     * (e o agendador de re-render iniciado, ver {@link SseHub#start()})
     * na primeira resolucao pelo Arc, tipicamente na primeira conexao SSE
     * real ({@link JtaSseRouteHandler}). {@code ApplicationScoped} para
     * existir uma unica instancia por aplicacao, igual aos demais beans
     * centrais deste producer.
     */
    @Produces
    @ApplicationScoped
    SseHub sseHub(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine) {
        SseHub hub = new SseHub(registry, invoker, templateEngine);
        hub.start();
        return hub;
    }

    void disposeSseHub(@Disposes SseHub hub) {
        hub.stop();
    }
}
