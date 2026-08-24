package dev.jta.demo.disciplinas;

import dev.jta.core.AComponent;
import dev.jta.core.Bindable;
import dev.jta.core.Redirect;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import dev.jta.demo.professores.Professor;
import dev.jta.demo.professores.ProfessorRepository;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/** Select de professor responsavel - primeiro formulario do demo com uma FK escolhida via dropdown, nao path param. */
@Route(value = "/disciplinas/novo", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/disciplinas\">&larr; Disciplinas</a></p>"
             + "<h1>Nova disciplina</h1>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Professor responsavel</label>"
             + "<select name=\"professorId\">"
             + "@for(var p : self.professores())"
             + "<option value=\"${p.getId()}\">${p.getNome()}</option>"
             + "@endfor"
             + "</select></div>"
             + "<button (click)=\"criar()\">Criar</button>"
             + "</main>"
)
public class DisciplinaNovo {

    @NotBlank(message = "Nome e obrigatorio")
    public String nome = "";

    @Bindable
    public String professorId = "";

    public Map<String, String> errors = Map.of();

    private final DisciplinaService service;
    private final ProfessorRepository professorRepository;

    public DisciplinaNovo(DisciplinaService service, ProfessorRepository professorRepository) {
        this.service = service;
        this.professorRepository = professorRepository;
    }

    public List<Professor> professores() {
        return professorRepository.findAll();
    }

    public void criar() {
        service.criar(nome, professorId);
        throw new Redirect("/disciplinas");
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }
}
