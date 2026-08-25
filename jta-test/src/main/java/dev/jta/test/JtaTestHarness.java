package dev.jta.test;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import dev.jta.runtime.ActionResult;
import dev.jta.runtime.ComponentFactory;
import dev.jta.runtime.ComponentInvoker;
import dev.jta.runtime.CurrentUser;
import dev.jta.runtime.JtaActionDispatcher;
import dev.jta.runtime.JtaPageDispatcher;
import dev.jta.runtime.PageResult;
import dev.jta.runtime.ReflectionComponentFactory;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import jakarta.validation.Validator;

import java.util.Map;

/**
 * Monta o mesmo trio {@code ComponentRegistry}/{@code TemplateEngine}/
 * {@code ComponentInvoker} que um starter real monta, e delega para
 * {@link JtaPageDispatcher}/{@link JtaActionDispatcher} de
 * {@code jta-runtime} - nenhuma logica de dispatch, seguranca ou binding e
 * reimplementada aqui, entao um teste escrito com este harness exercita
 * exatamente o mesmo caminho de codigo que roda em producao atras de
 * qualquer adaptador (Spring, Javalin, standalone, Quarkus).
 *
 * <p><b>Defaults</b> (equivalentes ao que {@code JtaAutoConfiguration} do
 * starter Spring monta): {@link ComponentRegistry#loadFromClasspath}
 * usando o classloader de contexto da thread atual, {@link TemplateEngine#createPrecompiled}
 * (assume que o build do consumidor ja rodou {@code jte-maven-plugin} -
 * goal {@code precompile} - sobre os {@code .jte} gerados pelo processor,
 * exatamente como qualquer app JTA real precisa fazer), {@link ReflectionComponentFactory}
 * (construtor sem argumentos - suficiente para a maioria dos componentes
 * de teste; um componente que precisa de DI via construtor exige
 * {@link #withComponentFactory}), sem {@link Validator} (Bean Validation
 * vira opcional/no-op, mesma regra de {@link JtaActionDispatcher}), e
 * {@link JtaConfig#empty()} (defaults do framework para o shell da pagina).
 *
 * <p>Uso tipico:
 * <pre>{@code
 * JtaTestHarness harness = JtaTestHarness.forClasspath();
 *
 * ActionResult result = harness.invokeAction(Contador.class, "incrementar",
 *         Map.of("valor", new String[]{"5"}));
 * String html = JtaAssertions.assertRendered(result);
 * JtaAssertions.assertContains(html, ">6<");
 * }</pre>
 */
public final class JtaTestHarness {

    private final ComponentRegistry registry;
    private ComponentFactory componentFactory = new ReflectionComponentFactory();
    private TemplateEngine templateEngine = TemplateEngine.createPrecompiled(ContentType.Html);
    private Validator validator;
    private JtaConfig config = JtaConfig.empty();

    private JtaTestHarness(ComponentRegistry registry) {
        this.registry = registry;
    }

    /** Carrega o {@link ComponentRegistry} do classloader de contexto da thread atual. */
    public static JtaTestHarness forClasspath() {
        return forClasspath(Thread.currentThread().getContextClassLoader());
    }

    /** Carrega o {@link ComponentRegistry} de um classloader especifico. */
    public static JtaTestHarness forClasspath(ClassLoader classLoader) {
        return new JtaTestHarness(ComponentRegistry.loadFromClasspath(classLoader));
    }

    /** Usa um {@link ComponentRegistry} ja montado, em vez de ler o classpath. */
    public static JtaTestHarness withRegistry(ComponentRegistry registry) {
        return new JtaTestHarness(registry);
    }

    /**
     * Troca o {@link ComponentFactory} - necessario para testar um
     * componente que recebe dependencias via construtor (ex: um servico
     * injetado), ja que o default ({@link ReflectionComponentFactory}) so
     * chama o construtor sem argumentos.
     */
    public JtaTestHarness withComponentFactory(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        return this;
    }

    /**
     * Troca o {@link TemplateEngine} - raramente necessario (o default
     * pre-compilado ja e o que producao usa); util para apontar para um
     * {@code TemplateEngine} de dev-mode (ver {@code JtaTemplateEngineFactory}
     * em jta-runtime) num teste que precise refletir edicoes de template
     * sem recompilar.
     */
    public JtaTestHarness withTemplateEngine(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        return this;
    }

    /** Liga a validacao Bean Validation (opcional - sem isso, {@code @NotBlank} etc. nunca bloqueiam uma acao no teste). */
    public JtaTestHarness withValidator(Validator validator) {
        this.validator = validator;
        return this;
    }

    /** Troca a {@link JtaConfig} usada para montar o shell da pagina (ex: testar {@code [features] tailwindcss = true}). */
    public JtaTestHarness withConfig(JtaConfig config) {
        this.config = config;
        return this;
    }

    public ComponentRegistry registry() {
        return registry;
    }

    /** Metadados de compile-time do componente, pelo FQN da classe. */
    public ComponentMetadata metadata(Class<?> componentType) {
        return registry.byFqn(componentType.getName());
    }

    /** Metadados de compile-time do componente, pelo selector (canonico ou explicito). */
    public ComponentMetadata metadata(String selector) {
        return registry.bySelector(selector);
    }

    /**
     * Instancia um componente diretamente (via {@link ComponentFactory}),
     * sem popular nenhum campo nem chamar {@code init()} - equivalente a
     * {@code new MeuComponente()} quando o {@link ComponentFactory} default
     * esta em uso. Util para testar metodos de template/getters isolados,
     * fora do ciclo de vida completo de uma requisicao.
     */
    public <T> T newInstance(Class<T> componentType) {
        return componentType.cast(componentFactory.instantiate(componentType));
    }

    /** {@link JtaPageDispatcher} montado com o registry/factory/engine/config atuais deste harness. */
    public JtaPageDispatcher pageDispatcher() {
        return new JtaPageDispatcher(registry, new ComponentInvoker(componentFactory), templateEngine, config);
    }

    /** {@link JtaActionDispatcher} montado com o registry/factory/engine/validator atuais deste harness. */
    public JtaActionDispatcher actionDispatcher() {
        return new JtaActionDispatcher(registry, new ComponentInvoker(componentFactory), templateEngine, validator);
    }

    /**
     * Renderiza a pagina de {@code componentType} (precisa de {@code @Route})
     * como um GET anonimo, sem query params/path variables - o caso comum
     * de "essa pagina renderiza sem quebrar".
     */
    public PageResult renderPage(Class<?> componentType) {
        return renderPage(componentType, Map.of(), Map.of(), CurrentUser.anonymous());
    }

    /** Renderiza a pagina de {@code componentType}, simulando query params/path variables/usuario. */
    public PageResult renderPage(Class<?> componentType, Map<String, String[]> queryParams,
                                  Map<String, String> pathVariables, CurrentUser user) {
        return pageDispatcher().dispatch(metadata(componentType), queryParams, pathVariables, user);
    }

    /**
     * Invoca {@code actionName} em {@code componentType} como um usuario
     * anonimo, sem parametros - o caso comum de "essa acao roda sem
     * quebrar a partir do estado inicial".
     */
    public ActionResult invokeAction(Class<?> componentType, String actionName) {
        return invokeAction(componentType, actionName, Map.of(), CurrentUser.anonymous());
    }

    /** Invoca {@code actionName} em {@code componentType}, simulando os parametros/usuario informados. */
    public ActionResult invokeAction(Class<?> componentType, String actionName, Map<String, String[]> params,
                                      CurrentUser user) {
        String selector = metadata(componentType).selector();
        return actionDispatcher().dispatch(selector, actionName, params, user);
    }
}
