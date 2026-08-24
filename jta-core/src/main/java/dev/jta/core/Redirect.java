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
 */
public final class Redirect extends RuntimeException {

    private final String path;

    public Redirect(String path) {
        super("redirect to " + path);
        this.path = path;
    }

    public String path() {
        return path;
    }
}
