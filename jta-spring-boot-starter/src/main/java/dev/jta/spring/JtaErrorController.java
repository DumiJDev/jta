package dev.jta.spring;

import dev.jta.runtime.JtaErrorPageRenderer;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Catch-all para erros que NUNCA passam por {@link JtaRouteRegistrar}/
 * {@code JtaActionController} - um path que nao bate com NENHUMA rota
 * registrada (nem JTA, nem do proprio app consumidor) e resolvido pelo
 * proprio Spring Boot re-despachando para {@code server.error.path}
 * (default {@code /error}), nunca chegando aos beans do JTA. Sem este
 * controller, esse caso caia sempre no Whitelabel/BasicErrorController
 * padrao do Spring Boot, mesmo com um componente {@code @ErrorPage}
 * registrado.
 *
 * <p>Mapeado explicitamente em {@code ${server.error.path:${error.path:/error}}}
 * (mesma convencao de propriedade que o {@code BasicErrorController} do
 * Spring Boot usa) para nao depender de nenhuma classe interna do
 * autoconfigure de erro do Spring Boot.
 */
@RestController
class JtaErrorController implements ErrorController {

    private final JtaErrorPageRenderer errorPageRenderer;

    JtaErrorController(JtaErrorPageRenderer errorPageRenderer) {
        this.errorPageRenderer = errorPageRenderer;
    }

    @RequestMapping("${server.error.path:${error.path:/error}}")
    ResponseEntity<String> handleError(HttpServletRequest request) {
        Object statusAttribute = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int statusCode = statusAttribute instanceof Integer i ? i : HttpStatus.INTERNAL_SERVER_ERROR.value();
        Object pathAttribute = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = pathAttribute instanceof String s ? s : null;

        Optional<String> html = errorPageRenderer.render(statusCode, path, null);
        HttpStatus status = HttpStatus.resolve(statusCode);
        HttpStatus resolvedStatus = status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
        if (html.isEmpty()) {
            // sem componente @ErrorPage registrado para este status: cai
            // no comportamento padrao do Spring Boot (Whitelabel), deixando
            // este handler simplesmente nao aplicar nenhum corpo custom -
            // devolvemos so o status, coerente com o resto do JTA (403/404
            // sem HTML quando nada esta registrado).
            return ResponseEntity.status(resolvedStatus).build();
        }
        return ResponseEntity.status(resolvedStatus).contentType(MediaType.TEXT_HTML).body(html.get());
    }
}
