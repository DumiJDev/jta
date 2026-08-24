package dev.jta.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Captura exceções internas do JTA (reflection de {@link JtaComponentInvoker},
 * lookups de {@link dev.jta.core.ComponentRegistry}) antes que cheguem cruas
 * ao cliente HTTP.
 *
 * <p><b>Por que isto e necessario:</b> sem um handler, uma
 * {@code IllegalArgumentException}/{@code IllegalStateException} lançada por
 * essas classes sobe como um 500 do Spring com a mensagem completa no corpo
 * (em ambientes com a página de erro padrão habilitada) - e essas mensagens
 * incluem detalhes internos como o valor bruto enviado pelo cliente, o nome
 * do campo/classe, etc. Baixo risco (não é uma vulnerabilidade de execução),
 * mas é vazamento de informação desnecessário para quem só está tentando
 * (ou atacando) um selector/action/parâmetro inválido.
 *
 * <p><b>Escopo deliberadamente restrito</b> via {@code assignableTypes}: só
 * se aplica a {@link JtaActionController} e {@link JtaRouteRegistrar} - as
 * duas classes do JTA que resolvem entrada 100% controlada pelo cliente
 * (selector, action, path/query params). Um {@code @RestControllerAdvice}
 * sem escopo se aplicaria também aos controllers do próprio app consumidor,
 * o que seria um efeito colateral perigoso para uma biblioteca (mudaria o
 * comportamento de exceções que nada têm a ver com o JTA).
 */
@RestControllerAdvice(assignableTypes = {JtaActionController.class, JtaRouteRegistrar.class})
class JtaExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(JtaExceptionHandler.class);

    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<Void> handleBadRequest(IllegalArgumentException e) {
        LOG.warn("Requisicao JTA invalida", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<Void> handleServerError(IllegalStateException e) {
        LOG.error("Falha interna ao processar requisicao JTA", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
