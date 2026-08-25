package dev.jta.spring;

import dev.jta.runtime.ActionResult;
import dev.jta.runtime.JtaActionDispatcher;
import dev.jta.runtime.csrf.CsrfRequest;
import dev.jta.runtime.csrf.CsrfTokenStore;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint generico de acoes HTMX: {@code POST /__jta/action/{selector}?action=...}.
 *
 * <p>Adaptador fino: so extrai os dados da {@link HttpServletRequest},
 * delega toda a orquestracao (verificacao de CSRF, resolucao do
 * componente, autorizacao, allowlist de acoes, validacao, invocacao,
 * render) para {@link JtaActionDispatcher} (jta-runtime, agnostico de
 * framework), e traduz o {@link ActionResult} devolvido para
 * {@link ResponseEntity}.
 */
@RestController
class JtaActionController {

    private static final Logger LOG = LoggerFactory.getLogger(JtaActionController.class);

    private final JtaActionDispatcher dispatcher;
    private final CsrfTokenStore csrfTokenStore;

    JtaActionController(JtaActionDispatcher dispatcher, CsrfTokenStore csrfTokenStore) {
        this.dispatcher = dispatcher;
        this.csrfTokenStore = csrfTokenStore;
    }

    @PostMapping("/__jta/action/{selector}")
    ResponseEntity<String> handleAction(@PathVariable("selector") String selector,
                                         @RequestParam("action") String action,
                                         HttpServletRequest request) {
        String cookieHeader = request.getHeader("Cookie");
        String csrfHeaderValue = request.getHeader(csrfTokenStore.headerName());
        CsrfRequest csrf = new CsrfRequest(cookieHeader, csrfHeaderValue);

        ActionResult result = dispatcher.dispatch(selector, action, request.getParameterMap(),
                SpringCurrentUser.current(), new ServletJtaSession(request.getSession(true)), csrf);

        if (result instanceof ActionResult.Forbidden) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        if (result instanceof ActionResult.NotFound) {
            // OWASP A09:2021: essa rejeicao e exatamente o sinal de uma
            // tentativa de exploracao do achado #1 (SECURITY.md) - sem
            // log, uma varredura de ?action=hashCode/wait/etc. nao
            // deixava rastro nenhum. 'action' e input 100% controlado
            // pelo cliente, entao sanitizado (sem CR/LF, truncado) antes
            // de logar, pra nao permitir log injection (forjar linhas de
            // log falsas).
            LOG.warn("Tentativa de invocar acao nao declarada '{}' em '{}'", sanitizeForLog(action), selector);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (result instanceof ActionResult.Redirect redirect) {
            return ResponseEntity.ok().header("HX-Redirect", redirect.path()).build();
        }
        ActionResult.Rendered rendered = (ActionResult.Rendered) result;
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(rendered.html());
    }

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "...(truncado)" : cleaned;
    }
}
