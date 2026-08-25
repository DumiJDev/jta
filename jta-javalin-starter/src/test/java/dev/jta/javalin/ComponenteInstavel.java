package dev.jta.javalin;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Fixture para a regressao do bug real encontrado na auditoria: o catch
 * de excecao no adaptador so cobria {@code IllegalArgumentException}/
 * {@code IllegalStateException} - qualquer outra {@code RuntimeException}
 * vinda de uma acao do consumidor escapava em vez de virar um 500 tratado
 * e registado.
 */
@Route("/instavel")
@AComponent(template = "<main><span>ok</span><button (click)=\"explodir()\">explodir</button></main>")
public class ComponenteInstavel {

    public void explodir() {
        String nulo = null;
        nulo.length();
    }
}
