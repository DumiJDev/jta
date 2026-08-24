package dev.jta.demo.professores;

import dev.jta.core.AComponent;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/** Cadastro de professores - restrito a ADMIN (dados de RH, nao publicos). */
@Route(value = "/professores", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<h1>Professores</h1>"
             + "<p><a href=\"/professores/novo\">+ Novo professor</a></p>"
             + "<table class=\"jta-table\">"
             + "<tr><th>Nome</th><th>Especialidade</th></tr>"
             + "@for(var p : self.professores())"
             + "<tr><td>${p.nome()}</td><td>${p.especialidade()}</td></tr>"
             + "@endfor"
             + "</table>"
             + "</main>"
)
public class ProfessorLista {

    private final ProfessorService service;

    public ProfessorLista(ProfessorService service) {
        this.service = service;
    }

    public List<ProfessorView> professores() {
        return service.listar().stream().map(ProfessorView::of).toList();
    }

    public record ProfessorView(String id, String nome, String especialidade) {
        static ProfessorView of(Professor p) {
            return new ProfessorView(p.getId(), p.getNome(), p.getEspecialidade());
        }
    }
}
