package dev.jta.demo.professores;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

@Route(value = "/professores/novo", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/professores\">&larr; Professores</a></p>"
             + "<h1>Novo professor</h1>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Especialidade</label>"
             + "<input type=\"text\" name=\"especialidade\" value=\"{{ especialidade }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemEspecialidade() }}</p>"
             + "<button (click)=\"criar()\">Criar</button>"
             + "</main>"
)
public class ProfessorNovo {

    @NotBlank(message = "Nome e obrigatorio")
    public String nome = "";

    @NotBlank(message = "Especialidade e obrigatoria")
    public String especialidade = "";

    public Map<String, String> errors = Map.of();

    private final ProfessorService service;

    public ProfessorNovo(ProfessorService service) {
        this.service = service;
    }

    public void criar() {
        service.criar(nome, especialidade);
        throw new Redirect("/professores");
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemEspecialidade() {
        return errors.getOrDefault("especialidade", "");
    }
}
