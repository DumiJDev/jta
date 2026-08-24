package dev.jta.demo.disciplinas;

import dev.jta.core.AComponent;
import dev.jta.core.AllowAnonymous;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Catalogo publico de disciplinas - junto com {@code TurmaLista}, prova que
 * {@code @AllowAnonymous} funciona de verdade (nenhuma role exigida, mesmo
 * com Spring Security ligado na aplicacao).
 */
@Route(value = "/disciplinas", layout = SiteLayout.class)
@AllowAnonymous
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<h1>Disciplinas</h1>"
             + "<table class=\"jta-table\">"
             + "<tr><th>Disciplina</th><th>Professor</th></tr>"
             + "@for(var d : self.disciplinas())"
             + "<tr><td>${d.nome()}</td><td>${d.professorNome()}</td></tr>"
             + "@endfor"
             + "</table>"
             + "</main>"
)
public class DisciplinaLista {

    private final DisciplinaService service;

    public DisciplinaLista(DisciplinaService service) {
        this.service = service;
    }

    public List<DisciplinaView> disciplinas() {
        return service.listar().stream().map(DisciplinaView::of).toList();
    }

    public record DisciplinaView(String id, String nome, String professorNome) {
        static DisciplinaView of(Disciplina d) {
            return new DisciplinaView(d.getId(), d.getNome(), d.getProfessor().getNome());
        }
    }
}
