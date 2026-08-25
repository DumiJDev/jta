package dev.jta.core;

/**
 * Lancada de dentro de um metodo de acao para sinalizar que, apos a acao
 * terminar, o cliente deve navegar para outra pagina - em vez de receber
 * o fragmento renderizado do proprio componente (o comportamento padrao).
 *
 * <p>Uso tipico: apos criar/atualizar/excluir um recurso, navegar de
 * volta para uma pagina de listagem.
 *
 * <pre>{@code
 * public void criar() {
 *     service.criar(nome, preco);
 *     throw new Redirect("/produtos");
 * }
 * }</pre>
 *
 * <p>O starter (ex: {@code JtaActionController} no Spring Boot) captura
 * esta excecao e responde com o header {@code HX-Redirect}, que o HTMX
 * segue automaticamente como uma navegacao de pagina inteira.
 *
 * <p><b>Flash messages:</b> {@link #withFlashSuccess}/{@link #withFlashError}
 * anexam uma mensagem de uso unico, guardada pelo runtime na sessao (ver
 * {@code JtaActionDispatcher}) e consumida (lida e removida) na proxima
 * pagina renderizada (ver {@code JtaPageDispatcher}), que a popula nos
 * campos reservados {@code flashSuccess}/{@code flashError} do componente
 * de destino (ver {@link ReservedFieldNames}). Nunca bindavel a partir de
 * uma requisicao - so o servidor, atraves desta classe, pode definir o
 * valor.
 */
public final class Redirect extends RuntimeException {

    private final String path;
    private final String flashSuccess;
    private final String flashError;

    public Redirect(String path) {
        this(path, null, null);
    }

    private Redirect(String path, String flashSuccess, String flashError) {
        super("redirect to " + path);
        this.path = path;
        this.flashSuccess = flashSuccess;
        this.flashError = flashError;
    }

    /** Redireciona para {@code path}, guardando {@code message} como flash de sucesso de uso unico. */
    public static Redirect withFlashSuccess(String path, String message) {
        return new Redirect(path, message, null);
    }

    /** Redireciona para {@code path}, guardando {@code message} como flash de erro de uso unico. */
    public static Redirect withFlashError(String path, String message) {
        return new Redirect(path, null, message);
    }

    public String path() {
        return path;
    }

    public String flashSuccess() {
        return flashSuccess;
    }

    public String flashError() {
        return flashError;
    }
}
