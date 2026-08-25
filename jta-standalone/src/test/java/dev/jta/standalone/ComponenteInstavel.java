package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Fixture para a regressao do bug real encontrado na auditoria: o catch
 * de excecao no adaptador so cobria {@code IllegalArgumentException}/
 * {@code IllegalStateException} - qualquer outra {@code RuntimeException}
 * lancada por codigo do consumidor (ex: um servico injetado, um metodo de
 * template) escapava e derrubava a conexao em vez de virar um 500 tratado
 * e registado.
 */
@Route("/instavel")
@AComponent(template = "<main><span>ok</span><button (click)=\"explodir()\">explodir</button></main>")
public class ComponenteInstavel {

    public void explodir() {
        // NullPointerException deliberada - representa qualquer excecao
        // que nao seja IllegalArgumentException/IllegalStateException,
        // vinda de codigo do proprio consumidor.
        String nulo = null;
        nulo.length();
    }
}
