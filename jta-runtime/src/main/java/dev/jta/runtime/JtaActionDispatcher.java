package dev.jta.runtime;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.LocaleContext;
import dev.jta.core.LocaleResolver;
import dev.jta.core.Redirect;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.validation.Validator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Orquestra o fluxo de uma acao HTMX (ver documento de arquitetura, secao
 * 5), agnostico de framework web: resolve o componente pelo selector,
 * autoriza, valida a acao contra o allowlist de compile-time, instancia,
 * reidrata o estado a partir dos parametros da requisicao, chama
 * {@code init()} se declarado, valida (se {@link Validator} estiver
 * disponivel), invoca a acao apenas se valido, e devolve um
 * {@link ActionResult} neutro - o adaptador (ex: {@code JtaActionController}
 * em jta-spring-boot-starter) traduz isso para a resposta HTTP do seu
 * framework.
 *
 * <p>Extraido de {@code JtaActionController} (jta-spring-boot-starter) na
 * extracao do nucleo agnostico - mesma sequencia de passos, mesmas duas
 * checagens de seguranca (achados #1 e #5 do SECURITY.md), agora
 * reutilizavel por qualquer adaptador sem reimplementar nada disso.
 *
 * <p><b>Validacao e opcional por design:</b> um {@link Validator} ausente
 * (passado como {@code null}) faz esse passo virar um no-op - nenhum
 * consumidor que nao tenha Bean Validation no classpath quebra por causa
 * desta feature.
 */
public final class JtaActionDispatcher {

    private final ComponentRegistry registry;
    private final ComponentInvoker invoker;
    private final TemplateEngine templateEngine;
    private final Validator validator;
    private final LocaleResolver localeResolver;

    public JtaActionDispatcher(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                Validator validator) {
        this(registry, invoker, templateEngine, validator, LocaleResolver.acceptLanguageOrDefault());
    }

    public JtaActionDispatcher(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                Validator validator, LocaleResolver localeResolver) {
        this.registry = registry;
        this.invoker = invoker;
        this.templateEngine = templateEngine;
        this.validator = validator;
        this.localeResolver = localeResolver;
    }

    public ActionResult dispatch(String selector, String action, Map<String, String[]> params, CurrentUser user) {
        return dispatch(selector, action, params, user, null);
    }

    /**
     * @param acceptLanguageHeader valor bruto do header HTTP {@code Accept-Language}
     *                             da requisicao, ou {@code null} se ausente/o
     *                             adaptador ainda nao repassa esse dado -
     *                             resolvido via {@link LocaleResolver} e
     *                             publicado em {@link LocaleContext} durante
     *                             toda a duracao desta chamada (ver
     *                             {@code Translations#translate}).
     */
    public ActionResult dispatch(String selector, String action, Map<String, String[]> params, CurrentUser user,
                                  String acceptLanguageHeader) {
        LocaleContext.set(localeResolver.resolve(acceptLanguageHeader));
        try {
            return doDispatch(selector, action, params, user);
        } finally {
            LocaleContext.clear();
        }
    }

    private ActionResult doDispatch(String selector, String action, Map<String, String[]> params, CurrentUser user) {
        ComponentMetadata metadata = registry.bySelector(selector);
        if (!SecurityEnforcer.isAuthorized(metadata, user)) {
            return new ActionResult.Forbidden();
        }
        // Vulnerabilidade real corrigida (ver SECURITY.md, achado #1): o
        // processor ja valida em compile-time exatamente quais metodos void
        // sao acoes legitimas (metadata.actions()) - sem checar isso aqui,
        // 'action' (controlado 100% pelo atacante) permitiria invocar
        // QUALQUER metodo publico sem argumentos da classe via reflection.
        if (!metadata.actions().contains(action)) {
            return new ActionResult.NotFound();
        }

        Object instance;
        try {
            Class<?> type = Class.forName(metadata.fqn());
            instance = invoker.instantiate(type);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Classe do componente nao encontrada: " + metadata.fqn(), e);
        }

        // erros de conversao (ex: 'abc' para um campo int) entram no mesmo
        // mapa que violacoes de Bean Validation - nunca mais propagam crus
        // (ver ComponentInvoker#setField/ConverterRegistry).
        Map<String, String> conversionErrors = invoker.populateFromParams(instance, params, Set.copyOf(metadata.bindableFields()));
        invoker.callInitIfPresent(instance);

        Map<String, String> validationErrors = validator != null ? invoker.validate(instance, validator) : Map.of();

        Map<String, String> errors = new LinkedHashMap<>(validationErrors);
        // erro de conversao prevalece sobre erro de validacao no mesmo
        // campo: se o valor nem chegou a virar um dado valido, a mensagem
        // mais util pro usuario e a de conversao, nao a da constraint
        // (que rodou contra o valor default do campo).
        errors.putAll(conversionErrors);
        invoker.applyErrors(instance, errors);

        // so invoca a acao se a validacao/conversao passou (ou se nao ha
        // validator configurado, ou seja, o dev nao optou por validacao
        // nenhuma) - dados invalidos nunca chegam ao codigo da acao.
        if (errors.isEmpty()) {
            try {
                invoker.invokeAction(instance, action);
            } catch (Redirect redirect) {
                return new ActionResult.Redirect(redirect.path());
            }
        }

        StringOutput output = new StringOutput();
        templateEngine.render(metadata.generatedJteTemplate(), instance, output);
        return new ActionResult.Rendered(output.toString());
    }
}
