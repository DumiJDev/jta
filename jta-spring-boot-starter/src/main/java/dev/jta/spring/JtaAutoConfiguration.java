package dev.jta.spring;

import dev.jta.core.AcceptLanguageLocaleResolver;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import dev.jta.core.LocaleResolver;
import dev.jta.runtime.ComponentInvoker;
import dev.jta.runtime.JtaActionDispatcher;
import dev.jta.runtime.JtaErrorPageRenderer;
import dev.jta.runtime.JtaPageDispatcher;
import dev.jta.runtime.JtaTemplateEngineFactory;
import dev.jta.runtime.csrf.CsrfTokenStore;
import dev.jta.runtime.csrf.CsrfTokenStoreFactory;
import gg.jte.TemplateEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Locale;

/**
 * Auto-configuracao do starter: liga o {@link ComponentRegistry} (lido do
 * classpath, gerado em compile-time pelo processor), o
 * {@link TemplateEngine} do JTE em modo <b>pre-compilado</b>, os
 * dispatchers agnosticos de framework de {@code jta-runtime}, e os beans
 * internos de despacho de acao/rota (adaptadores finos do Spring MVC em
 * cima desses dispatchers).
 *
 * <p>O dev nao precisa configurar nada disso manualmente - adicionar a
 * dependencia do starter e suficiente (ver secao 13 do documento de
 * arquitetura: "zero boilerplate").
 *
 * <p><b>Importante:</b> o {@code jte-maven-plugin} (goal {@code precompile},
 * configurado no pom do modulo consumidor) ja compila os {@code .jte}
 * gerados pelo processor em bytecode durante o build, para dentro de
 * {@code target/classes}. Por isso o {@link TemplateEngine} aqui usa
 * {@link JtaTemplateEngineFactory}, que por padrao monta o modo
 * pre-compilado ({@link TemplateEngine#createPrecompiled}, que carrega
 * essas classes ja compiladas via classloader) - e so troca para o modo
 * de dev-loop (recompilacao sob demanda a partir do
 * {@code .jte}/{@code .jta} fonte, sem restart da JVM) quando
 * explicitamente ligado via {@code -Djta.dev=true} ou
 * {@code [dev] enabled = true} em {@code jta.config.toml} - ver o javadoc
 * de {@link JtaTemplateEngineFactory} para o porque o modo "on-demand" cru
 * do JTE ({@link TemplateEngine#create}, sem o classpath da aplicacao)
 * nao funciona aqui (causava {@code package dev.jta.demo does not exist}
 * em runtime).
 *
 * <p><b>{@code before = ErrorMvcAutoConfiguration.class}:</b> necessario
 * para {@link JtaErrorController} (mapeado em {@code /error}) substituir o
 * {@code BasicErrorController} default do Spring Boot em vez de colidir
 * com ele - {@code ErrorMvcAutoConfiguration.basicErrorController} so e
 * registrado se NENHUM bean {@link ErrorController} existir ainda
 * ({@code @ConditionalOnMissingBean(ErrorController.class)} no proprio
 * Spring Boot), o que so funciona se esta auto-configuracao (que registra
 * {@link JtaErrorController}) for processada ANTES dela. Sem isto, os
 * dois controllers acabavam mapeados no mesmo path e o contexto falhava
 * ao subir ({@code IllegalStateException: Ambiguous mapping}).
 */
@AutoConfiguration(before = ErrorMvcAutoConfiguration.class)
public class JtaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ComponentRegistry jtaComponentRegistry() {
        return ComponentRegistry.loadFromClasspath(Thread.currentThread().getContextClassLoader());
    }

    @Bean
    @ConditionalOnMissingBean
    public JtaConfig jtaConfig() {
        return JtaConfig.loadFromClasspath(Thread.currentThread().getContextClassLoader());
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateEngine jtaTemplateEngine(JtaConfig config) {
        return JtaTemplateEngineFactory.create(config);
    }

    /**
     * Resolve o locale de cada requisicao a partir do header
     * {@code Accept-Language}, caindo no locale configurado em
     * {@code jta.config.toml} ({@code [i18n] default_locale = "pt"}, tag
     * BCP 47) ou em {@link Locale#getDefault()} se nao configurado - ver
     * {@code Translations}/{@code LocaleContext} para onde isso e
     * consumido.
     */
    @Bean
    @ConditionalOnMissingBean
    public LocaleResolver jtaLocaleResolver(JtaConfig config) {
        String tag = config.getString("i18n", "default_locale", "");
        Locale defaultLocale = tag.isBlank() ? Locale.getDefault() : Locale.forLanguageTag(tag);
        return new AcceptLanguageLocaleResolver(defaultLocale);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentInvoker jtaComponentInvoker(ApplicationContext ctx) {
        return new ComponentInvoker(new SpringComponentFactory(ctx));
    }

    @Bean
    @ConditionalOnMissingBean
    public CsrfTokenStore jtaCsrfTokenStore(JtaConfig config) {
        return CsrfTokenStoreFactory.create(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public JtaActionDispatcher jtaActionDispatcher(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                                     ObjectProvider<jakarta.validation.Validator> validatorProvider,
                                                     CsrfTokenStore csrfTokenStore, LocaleResolver localeResolver) {
        return new JtaActionDispatcher(registry, invoker, templateEngine, validatorProvider.getIfAvailable(),
                csrfTokenStore, localeResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public JtaPageDispatcher jtaPageDispatcher(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                                 JtaConfig config, CsrfTokenStore csrfTokenStore,
                                                 LocaleResolver localeResolver) {
        return new JtaPageDispatcher(registry, invoker, templateEngine, config, csrfTokenStore, localeResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public JtaErrorPageRenderer jtaErrorPageRenderer(ComponentRegistry registry, ComponentInvoker invoker,
                                                       TemplateEngine templateEngine, JtaConfig config) {
        return new JtaErrorPageRenderer(registry, invoker, templateEngine, config);
    }

    // JtaActionController e JtaRouteRegistrar sao @RestController/beans
    // escaneados normalmente pelo component-scan do Spring Boot a partir
    // do pacote dev.jta.spring, que fica fora do component-scan default
    // do app do dev - por isso este pacote precisa estar incluido via
    // @ComponentScan adicional ou (mais simples, e o que o starter faz)
    // sendo importado explicitamente aqui como beans regulares.
    @Bean
    @ConditionalOnMissingBean
    JtaActionController jtaActionController(JtaActionDispatcher dispatcher, CsrfTokenStore csrfTokenStore) {
        return new JtaActionController(dispatcher, csrfTokenStore);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaRouteRegistrar jtaRouteRegistrar(ComponentRegistry registry, JtaPageDispatcher dispatcher,
                                         RequestMappingHandlerMapping handlerMapping,
                                         JtaErrorPageRenderer errorPageRenderer) {
        return new JtaRouteRegistrar(registry, dispatcher, handlerMapping, errorPageRenderer);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaSseController jtaSseController(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                       RequestMappingHandlerMapping handlerMapping) {
        return new JtaSseController(registry, invoker, templateEngine, handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaExceptionHandler jtaExceptionHandler(JtaErrorPageRenderer errorPageRenderer) {
        return new JtaExceptionHandler(errorPageRenderer);
    }

    // Mesmo motivo do comentario acima de JtaActionController/JtaRouteRegistrar:
    // JtaErrorController tambem vive em dev.jta.spring, fora do component-scan
    // do app consumidor - sem este @Bean explicito, o catch-all de /error
    // nunca seria registrado e paginas @ErrorPage nunca renderizariam para
    // erros que nao passam por JtaRouteRegistrar/JtaActionController (ex: 404
    // de um path que nao bate com nenhuma rota).
    //
    // @ConditionalOnMissingBean(ErrorController.class), NAO o default (que
    // checaria por JtaErrorController.class): recua se o proprio app
    // consumidor ja declarou seu proprio ErrorController - ver o javadoc da
    // classe sobre "before = ErrorMvcAutoConfiguration.class" para o outro
    // lado desta mesma substituicao (o BasicErrorController default).
    @Bean
    @ConditionalOnMissingBean(ErrorController.class)
    JtaErrorController jtaErrorController(JtaErrorPageRenderer errorPageRenderer) {
        return new JtaErrorController(errorPageRenderer);
    }
}
