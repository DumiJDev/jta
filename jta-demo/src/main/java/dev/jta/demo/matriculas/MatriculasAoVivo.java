package dev.jta.demo.matriculas;

import dev.jta.core.AComponent;
import dev.jta.core.AllowAnonymous;
import dev.jta.core.Sse;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Widget ao vivo da home, via {@code @Sse} - o unico recurso desta entrega
 * que so o starter Spring suporta (ver {@code JtaSseController}). Cada
 * re-render agendado cria uma instancia nova (sem estado de requisicao),
 * por isso o servico injetado via construtor e a unica forma de ler dado
 * atual - nao ha path/query param nenhum aqui.
 */
@Sse(value = "/sse/matriculas", intervalMillis = 3000)
@AllowAnonymous
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<span id=\"contador-matriculas\">{{ total() }} matricula(s) ativa(s)</span>"
)
public class MatriculasAoVivo {

    private final MatriculaService service;

    public MatriculasAoVivo(MatriculaService service) {
        this.service = service;
    }

    public long total() {
        return service.total();
    }
}
