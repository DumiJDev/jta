package dev.jta.spring;

import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuracao do starter: liga o {@link ComponentRegistry} (lido do
 * classpath, gerado em compile-time pelo processor), o
 * {@link TemplateEngine} do JTE em modo <b>pre-compilado</b>, e os beans
 * internos de despacho de acao/rota.
 *
 * <p>O dev nao precisa configurar nada disso manualmente - adicionar a
 * dependencia do starter e suficiente (ver secao 13 do documento de
 * arquitetura: "zero boilerplate").
 *
 * <p><b>Importante:</b> o {@code jte-maven-plugin} (goal {@code precompile},
 * configurado no pom do modulo consumidor) ja compila os {@code .jte}
 * gerados pelo processor em bytecode durante o build, para dentro de
 * {@code target/classes}. Por isso o {@link TemplateEngine} aqui precisa
 * estar no modo pre-compilado ({@link TemplateEngine#createPrecompiled}),
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

    // JtaComponentInvoker, JtaActionController e JtaRouteRegistrar sao
    // @Component/@RestController escaneados normalmente pelo
    // component-scan do Spring Boot a partir do pacote dev.jta.spring,
    // que fica fora do component-scan default do app do dev - por isso
    // este pacote precisa estar incluido via @ComponentScan adicional ou
    // (mais simples, e o que o starter faz) sendo importado explicitamente
    // aqui como beans regulares.
    @Bean
    @ConditionalOnMissingBean
    JtaComponentInvoker jtaComponentInvoker(org.springframework.context.ApplicationContext ctx) {
        return new JtaComponentInvoker(ctx);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaActionController jtaActionController(ComponentRegistry registry, JtaComponentInvoker invoker, TemplateEngine templateEngine,
                                              org.springframework.beans.factory.ObjectProvider<jakarta.validation.Validator> validatorProvider) {
        return new JtaActionController(registry, invoker, templateEngine, validatorProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaRouteRegistrar jtaRouteRegistrar(ComponentRegistry registry, JtaComponentInvoker invoker, TemplateEngine templateEngine,
                                         JtaConfig config,
                                         org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping) {
        return new JtaRouteRegistrar(registry, invoker, templateEngine, config, handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaSseController jtaSseController(ComponentRegistry registry, JtaComponentInvoker invoker, TemplateEngine templateEngine,
                                       org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping) {
        return new JtaSseController(registry, invoker, templateEngine, handlerMapping);
    }

    @Bean
    @ConditionalOnMissingBean
    JtaExceptionHandler jtaExceptionHandler() {
        return new JtaExceptionHandler();
    }
}
