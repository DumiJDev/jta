package dev.jta.runtime;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.Redirect;
import dev.jta.runtime.csrf.CsrfRequest;
import dev.jta.runtime.csrf.CsrfTokenStore;
import dev.jta.runtime.session.JtaSession;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;

/**
 * Orquestra o fluxo de uma acao HTMX (ver documento de arquitetura, secao
 * 5), agnostico de framework web: verifica CSRF, resolve o componente pelo
 * selector, autoriza, valida a acao contra o allowlist de compile-time,
 * instancia, reidrata o estado a partir dos parametros da requisicao (mais
 * a sessao resolvida pelo adaptador), chama {@code init()} se declarado,
 * valida (se {@link Validator} estiver disponivel), invoca a acao apenas se
 * valido, e devolve um {@link ActionResult} neutro - o adaptador (ex:
 * {@code JtaActionController} em jta-spring-boot-starter) traduz isso para
 * a resposta HTTP do seu framework.
 *
 * <p>Extraido de {@code JtaActionController} (jta-spring-boot-starter) na
 * extracao do nucleo agnostico - mesma sequencia de passos, mesmas duas
 * checagens de seguranca (achados #1 e #5 do SECURITY.md), agora
 * reutilizavel por qualquer adaptador sem reimplementar nada disso.
 *
 * <p><b>CSRF (ver SECURITY.md, achado #6):</b> verificado ANTES de
 * {@link SecurityEnforcer#isAuthorized} - e a camada mais barata (nenhum
 * lookup de role/instancia envolvido) e deve falhar primeiro. Pulado
 * quando {@code metadata.csrfExempt()} e {@code true} (ver
 * {@code dev.jta.core.CsrfExempt}).
 *
 * <p><b>Validacao e opcional por design:</b> um {@link Validator} ausente
 * (passado como {@code null}) faz esse passo virar um no-op - nenhum
 * consumidor que nao tenha Bean Validation no classpath quebra por causa
 * desta feature.
 */
public final class JtaActionDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(JtaActionDispatcher.class);

    private final ComponentRegistry registry;
    private final ComponentInvoker invoker;
    private final TemplateEngine templateEngine;
    private final Validator validator;
    private final CsrfTokenStore csrfTokenStore;

    public JtaActionDispatcher(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine,
                                Validator validator, CsrfTokenStore csrfTokenStore) {
        this.registry = registry;
        this.invoker = invoker;
        this.templateEngine = templateEngine;
        this.validator = validator;
        this.csrfTokenStore = csrfTokenStore;
    }

    public ActionResult dispatch(String selector, String action, Map<String, String[]> params, CurrentUser user,
                                  JtaSession session, CsrfRequest csrf) {
        ComponentMetadata metadata = registry.bySelector(selector);

        if (!metadata.csrfExempt() && !csrfTokenStore.verify(csrf.cookieHeader(), csrf.headerValue())) {
            // ver SECURITY.md, achado #6: prova dupla - a cookie precisa ter
            // sido emitida por nos (assinatura HMAC bate) E o header
            // precisa bater com o token da cookie (prova que quem fez o
            // pedido tinha acesso ao HTML da pagina, logo e mesma origem).
            LOG.warn("CSRF invalido em '{}'", metadata.selector());
            return new ActionResult.Forbidden();
        }
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

        invoker.populateFromParams(instance, params, Set.copyOf(metadata.bindableFields()));
        invoker.applySession(instance, session);
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
