package dev.jta.spring;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.Redirect;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint generico de acoes HTMX: {@code POST /__jta/action/{selector}?action=...}.
 *
 * <p>Fluxo (ver documento de arquitetura, secao 5): resolve o componente
 * pelo selector, instancia, reidrata o estado a partir dos parametros da
 * requisicao (o restante da query string, ja que o estado e gerenciado
 * pelo backend por reenvio), chama {@code init()} se declarado, valida
 * (se Jakarta Validation estiver disponivel), invoca a acao apenas se
 * valido, e ou (a) devolve o fragmento renderizado para o HTMX trocar via
 * {@code hx-swap="outerHTML"}, ou (b) se a acao sinalizou
 * {@link Redirect}, devolve o header {@code HX-Redirect} para o HTMX
 * seguir como navegacao de pagina inteira.
 *
 * <p><b>Validacao e opcional por design:</b> o {@link Validator} e
 * injetado via {@link ObjectProvider} (nao diretamente), entao um projeto
 * sem {@code spring-boot-starter-validation} no classpath simplesmente
 * nunca tem um bean {@code Validator} disponivel e o passo inteiro vira
 * um no-op - nenhum consumidor existente quebra por causa desta feature.
 */
@RestController
class JtaActionController {

    private static final Logger LOG = LoggerFactory.getLogger(JtaActionController.class);

    private final ComponentRegistry registry;
    private final JtaComponentInvoker invoker;
    private final TemplateEngine templateEngine;
    private final ObjectProvider<Validator> validatorProvider;

    JtaActionController(ComponentRegistry registry, JtaComponentInvoker invoker, TemplateEngine templateEngine,
                         ObjectProvider<Validator> validatorProvider) {
        this.registry = registry;
        this.invoker = invoker;
        this.templateEngine = templateEngine;
        this.validatorProvider = validatorProvider;
    }

    @PostMapping("/__jta/action/{selector}")
    ResponseEntity<String> handleAction(@PathVariable("selector") String selector,
                                         @RequestParam("action") String action,
                                         HttpServletRequest request) {
        ComponentMetadata metadata = registry.bySelector(selector);
        if (!JtaSecurityEnforcer.isAuthorized(metadata)) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN).build();
        }
        // Vulnerabilidade real corrigida (ver SECURITY.md, achado #1): o
        // processor ja valida em compile-time exatamente quais metodos void
        // sao acoes legitimas (metadata.actions()) - sem checar isso aqui,
        // 'action' (controlado 100% pelo atacante via query param) permitia
        // invocar QUALQUER metodo publico sem argumentos da classe via
        // reflection, incluindo metodos de template e ate herdados de
        // Object (wait(), hashCode(), etc.) - nao so as acoes declaradas.
        if (!metadata.actions().contains(action)) {
            // OWASP A09:2021: essa rejeicao e exatamente o sinal de uma
            // tentativa de exploracao do achado #1 (SECURITY.md) - sem log,
            // uma varredura de ?action=hashCode/wait/etc. nao deixava
            // rastro nenhum. 'action' e input 100% controlado pelo cliente,
            // entao sanitizado (sem CR/LF, truncado) antes de logar, pra
            // nao permitir forjar linhas de log (log injection).
            LOG.warn("Tentativa de invocar acao nao declarada '{}' em '{}'", sanitizeForLog(action), selector);
            return ResponseEntity.status(org.springframework.http.HttpStatus.NOT_FOUND).build();
        }

        Object instance;
        try {
            Class<?> type = Class.forName(metadata.fqn());
            instance = invoker.instantiate(type);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Classe do componente nao encontrada: " + metadata.fqn(), e);
        }

        invoker.populateFromParams(instance, request.getParameterMap(), java.util.Set.copyOf(metadata.bindableFields()));
        invoker.callInitIfPresent(instance);

        Validator validator = validatorProvider.getIfAvailable();
        Map<String, String> errors = validator != null ? invoker.validate(instance, validator) : Map.of();
        invoker.applyErrors(instance, errors);

        // so invoca a acao se a validacao passou (ou se nao ha validator
        // configurado, ou seja, o dev nao optou por validacao nenhuma) -
        // dados invalidos nunca chegam ao codigo da acao.
        if (errors.isEmpty()) {
            try {
                invoker.invokeAction(instance, action);
            } catch (Redirect redirect) {
                return ResponseEntity.ok().header("HX-Redirect", redirect.path()).build();
            }
        }

        StringOutput output = new StringOutput();
        templateEngine.render(metadata.generatedJteTemplate(), instance, output);

        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(output.toString());
    }

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "...(truncado)" : cleaned;
    }
}
