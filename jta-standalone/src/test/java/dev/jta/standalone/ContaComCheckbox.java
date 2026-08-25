package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Fixture para a regressao do bug real encontrado na auditoria:
 * {@code Boolean.parseBoolean} so reconhece a string literal
 * {@code "true"}, mas um {@code <input type="checkbox">} sem atributo
 * {@code value} explicito - o caso mais comum em HTML - envia
 * {@code "on"} quando marcado. O checkbox ficava silenciosamente
 * {@code false} por mais que o utilizador o marcasse.
 */
@Route("/conta")
@AComponent(template = "<main>"
        + "<input type=\"hidden\" name=\"ativo\" value=\"{{ ativo }}\"/>"
        + "<span id=\"ativo\">{{ ativo }}</span>"
        + "<button (click)=\"alternar()\">alternar</button>"
        + "</main>")
public class ContaComCheckbox {
    public boolean ativo = false;

    public void alternar() {
        // no-op: o teste so exercita a reidratacao do campo a partir do
        // valor de checkbox enviado, nao a acao em si.
    }
}
