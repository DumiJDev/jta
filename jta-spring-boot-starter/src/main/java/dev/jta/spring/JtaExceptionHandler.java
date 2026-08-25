package dev.jta.spring;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
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

    /**
     * Deixa as excecoes do Spring Security passarem intactas.
     *
     * <p>Sem isto, o handler generico de {@code Exception} abaixo engoliria
     * uma {@code AccessDeniedException} (lancada, por exemplo, por
     * {@code @PreAuthorize} num servico do consumidor chamado de dentro de
     * uma acao) e devolveria 500. Um 403 legitimo viraria um 500 - uma
     * regressao de seguranca silenciosa introduzida por um handler de
     * erro.
     *
     * <p>A causa e de ordem de execucao: o {@code ExceptionTranslationFilter}
     * que traduz estas excecoes em 401/403 e um filtro de servlet, logo corre
     * <b>por fora</b> do {@code DispatcherServlet} - e um
     * {@code @ExceptionHandler} corre por dentro. Quem apanha primeiro
     * decide, entao o JTA tem de recusar explicitamente estas duas
     * familias e deixa-las subir ate ao filtro.
     *
     * <p>O {@code @ExceptionHandler} mais especifico ganha em Spring, por
     * isso basta declara-las aqui e relanca-las.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    ResponseEntity<Void> rethrowSecurityExceptions(RuntimeException e) {
        throw e;
    }

    /**
     * Apanha {@code Exception}, nao apenas {@code IllegalStateException}.
     *
     * <p>Antes desta correcao, o handler cobria so as duas excecoes que o
     * proprio JTA lanca - mas o codigo que corre por baixo destas duas
     * classes inclui codigo do consumidor: metodos de template, {@code init()}
     * e acoes, todos podendo lancar qualquer coisa. Uma
     * {@code NullPointerException} vinda de um servico injetado escapava
     * daqui e caia no Whitelabel do Spring, exatamente o vazamento de
     * detalhe interno que este handler existe para evitar - e sem o log
     * deste lado.
     *
     * <p>O {@code assignableTypes} do {@code @RestControllerAdvice} continua
     * a limitar tudo isto as duas classes do JTA: alargar a excecao apanhada
     * <b>nao</b> alarga o conjunto de controllers afetados, portanto os
     * {@code @RestController} do proprio consumidor ficam intocados.
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<Void> handleServerError(Exception e) {
        LOG.error("Falha interna ao processar requisicao JTA", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}
