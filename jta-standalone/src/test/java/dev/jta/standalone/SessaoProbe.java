package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.Route;
import dev.jta.runtime.session.JtaSession;

/**
 * Fixture minimo para provar a sessao agnostica (ver {@code ComponentInvoker#applySession}):
 * uma acao guarda um contador num atributo de sessao, e o template le esse
 * mesmo atributo de volta - sem nenhum campo publico bindavel envolvido, so
 * o campo reservado {@code session}.
 */
@Route("/sessao")
@AComponent(template = "<main><span id=\"contagem\">{{ contagem() }}</span></main>")
public class SessaoProbe {

    public JtaSession session;

    public String contagem() {
        Object valor = session != null ? session.attribute("contagem") : null;
        return valor == null ? "0" : valor.toString();
    }

    public void incrementar() {
        Object atual = session.attribute("contagem");
        int novo = (atual == null ? 0 : (Integer) atual) + 1;
        session.setAttribute("contagem", novo);
    }
}
