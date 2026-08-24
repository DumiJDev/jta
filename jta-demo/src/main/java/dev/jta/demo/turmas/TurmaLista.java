package dev.jta.demo.turmas;

import dev.jta.core.AComponent;
import dev.jta.core.AllowAnonymous;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Catalogo publico de turmas - {@code @AllowAnonymous} explicito (sem
 * login nenhum), enquanto {@code /turmas/novo} e {@code /turmas/{id}/editar}
 * (mesmo prefixo de path) exigem ADMIN - a mesma pagina "aberta + cadastro
 * protegido" que o starter Spring ja provava no demo anterior.
 */
@Route(value = "/turmas", layout = SiteLayout.class)
@AllowAnonymous
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<h1>Turmas</h1>"
             + "<p><a href=\"/turmas/novo\">+ Nova turma</a> (requer login como administrador)</p>"
             + "<table class=\"jta-table\">"
             + "<tr><th>Turma</th><th>Ano letivo</th><th></th></tr>"
             + "@for(var t : self.turmas())"
             + "<tr><td>${t.nome()}</td><td>${t.ano()}</td>"
             + "<td><a href=\"/turmas/${t.id()}/editar\">editar</a></td></tr>"
             + "@endfor"
             + "</table>"
             + "</main>"
)
public class TurmaLista {

    private final TurmaService service;

    public TurmaLista(TurmaService service) {
        this.service = service;
    }

    public List<TurmaView> turmas() {
        return service.listar().stream().map(TurmaView::of).toList();
    }

    public record TurmaView(String id, String nome, String ano) {
        static TurmaView of(Turma t) {
            return new TurmaView(t.getId(), t.getNome(), t.getAno());
        }
    }
}
