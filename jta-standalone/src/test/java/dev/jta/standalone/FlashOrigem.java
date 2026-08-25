package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.Route;

/**
 * Fixture minimo para provar flash messages de uso unico (ver
 * {@code Redirect#withFlashSuccess}, {@code FlashSupport} em jta-runtime):
 * uma acao redireciona para {@link FlashDestino} carregando uma mensagem
 * de sucesso.
 */
@Route("/flash-origem")
@AComponent(template = "<main>origem</main>")
public class FlashOrigem {

    public void disparar() {
        throw Redirect.withFlashSuccess("/flash-destino", "Operacao concluida!");
    }
}
