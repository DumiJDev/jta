package dev.jta.spring;

import dev.jta.runtime.ActionResult;
import dev.jta.runtime.JtaActionDispatcher;
import dev.jta.runtime.csrf.CsrfRequest;
import dev.jta.runtime.csrf.CsrfTokenStore;
import dev.jta.runtime.upload.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

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
                                         HttpServletRequest request) throws IOException {
        String cookieHeader = request.getHeader("Cookie");
        String csrfHeaderValue = request.getHeader(csrfTokenStore.headerName());
        CsrfRequest csrf = new CsrfRequest(cookieHeader, csrfHeaderValue);

        ActionResult result = dispatcher.dispatch(selector, action, request.getParameterMap(),
                SpringCurrentUser.current(), new ServletJtaSession(request.getSession(true)), csrf, extractUploads(request));

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

    /**
     * Extrai as partes de arquivo de uma requisicao {@code multipart/form-data}
     * (ver {@code ComponentMetadata#uploadFields}) - {@code Part#getSubmittedFileName()}
     * distingue uma parte de ARQUIVO de um campo de texto comum enviado via
     * multipart (que ja chega em {@code request.getParameterMap()} normalmente,
     * populado pelo proprio resolvedor de multipart do Spring Boot, entao nao
     * precisa ser repetido aqui). {@code request.getParts()} exige que a
     * requisicao seja multipart-configurada - Spring Boot habilita isso por
     * padrao ({@code spring.servlet.multipart.enabled=true}), entao so
     * tentamos quando o Content-Type de fato declara multipart.
     */
    private static Map<String, UploadedFile> extractUploads(HttpServletRequest request) throws IOException {
        String contentType = request.getContentType();
        if (contentType == null || !contentType.toLowerCase().startsWith("multipart/")) {
            return Map.of();
        }
        Map<String, UploadedFile> uploads = new HashMap<>();
        try {
            for (Part part : request.getParts()) {
                if (part.getSubmittedFileName() != null) {
                    uploads.put(part.getName(), new UploadedFile(part.getSubmittedFileName(), part.getContentType(),
                            part.getInputStream().readAllBytes()));
                }
            }
        } catch (jakarta.servlet.ServletException e) {
            throw new IOException("Falha ao ler partes multipart da requisicao", e);
        }
        return uploads;
    }

    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", "_");
        return cleaned.length() > 100 ? cleaned.substring(0, 100) + "...(truncado)" : cleaned;
    }
}
