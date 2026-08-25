package dev.jta.runtime;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.Redirect;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.validation.Validator;

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

    public JtaActionDispatcher(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                Validator validator) {
        this.registry = registry;
        this.invoker = invoker;
        this.templateEngine = templateEngine;
        this.validator = validator;
    }

    public ActionResult dispatch(String selector, String action, Map<String, String[]> params, CurrentUser user) {
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
            // Mesmo motivo de JtaPageDispatcher/SseHub: usa o classloader de
            // contexto da thread, nao o do chamador (Class.forName(fqn) de 1
            // argumento) - sob o classloading em varias camadas do Quarkus
            // (QuarkusClassLoader), o classloader de JtaActionDispatcher
            // (modulo jta-runtime, camada "base") nao enxerga as classes da
            // aplicacao, so o classloader de contexto da thread de
            // requisicao (setado pelo proprio Quarkus) enxerga - achado real
            // rodando o TCK do Quarkus (todo POST de acao devolvia 500).
            Class<?> type = Class.forName(metadata.fqn(), true, Thread.currentThread().getContextClassLoader());
            instance = invoker.instantiate(type);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Classe do componente nao encontrada: " + metadata.fqn(), e);
        }

        invoker.populateFromParams(instance, params, Set.copyOf(metadata.bindableFields()));
        invoker.callInitIfPresent(instance);

        Map<String, String> errors = validator != null ? invoker.validate(instance, validator) : Map.of();
        invoker.applyErrors(instance, errors);

        // so invoca a acao se a validacao passou (ou se nao ha validator
        // configurado, ou seja, o dev nao optou por validacao nenhuma) -
        // dados invalidos nunca chegam ao codigo da acao.
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
