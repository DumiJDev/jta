package dev.jta.spring;

import dev.jta.core.AcceptLanguageLocaleResolver;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import dev.jta.core.LocaleResolver;
import dev.jta.runtime.ComponentInvoker;
import dev.jta.runtime.JtaActionDispatcher;
import dev.jta.runtime.JtaPageDispatcher;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
 * {@code target/classes}. Por isso o {@link TemplateEngine} aqui precisa
 * estar no modo pre-compilado ({@link TemplateEngine#createPrecompiled},
 * que carrega essas classes ja compiladas via classloader - e NAO no modo
 * "on-demand" ({@link TemplateEngine#create}), que tentaria recompilar o
 * {@code .jte} em runtime a partir do source, usando um classpath isolado
 * que nao enxerga as classes da propria aplicacao (isso e exatamente o que
 * causava {@code package dev.jta.demo does not exist} em runtime).
 */
@AutoConfiguration
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
    public TemplateEngine jtaTemplateEngine() {
        return TemplateEngine.createPrecompiled(ContentType.Html);
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
    public JtaActionDispatcher jtaActionDispatcher(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                                     ObjectProvider<jakarta.validation.Validator> validatorProvider,
                                                     LocaleResolver localeResolver) {
        return new JtaActionDispatcher(registry, invoker, templateEngine, validatorProvider.getIfAvailable(), localeResolver);
    }

    @Bean
    @ConditionalOnMissingBean
    public JtaPageDispatcher jtaPageDispatcher(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                                 JtaConfig config, LocaleResolver localeResolver) {
        return new JtaPageDispatcher(registry, invoker, templateEngine, config, localeResolver);
    }

    // JtaActionController e JtaRouteRegistrar sao @RestController/beans
    // escaneados normalmente pelo component-scan do Spring Boot a partir
    // do pacote dev.jta.spring, que fica fora do component-scan default
    // do app do dev - por isso este pacote precisa estar incluido via
    // @ComponentScan adicional ou (mais simples, e o que o starter faz)
    // sendo importado explicitamente aqui como beans regulares.
    @Bean
    @ConditionalOnMissingBean
    JtaActionController jtaActionController(JtaActionDispatcher dispatcher) {
        return new JtaActionController(dispatcher);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaRouteRegistrar jtaRouteRegistrar(ComponentRegistry registry, JtaPageDispatcher dispatcher,
                                         RequestMappingHandlerMapping handlerMapping) {
        return new JtaRouteRegistrar(registry, dispatcher, handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaSseController jtaSseController(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                       RequestMappingHandlerMapping handlerMapping) {
        return new JtaSseController(registry, invoker, templateEngine, handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaExceptionHandler jtaExceptionHandler() {
        return new JtaExceptionHandler();
    }
}
