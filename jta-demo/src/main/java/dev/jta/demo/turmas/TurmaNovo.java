package dev.jta.demo.turmas;

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

@Route(value = "/turmas/novo", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/turmas\">&larr; Turmas</a></p>"
             + "<h1>Nova turma</h1>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Ano letivo</label>"
             + "<input type=\"text\" name=\"ano\" value=\"{{ ano }}\" placeholder=\"2026\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemAno() }}</p>"
             + "<button (click)=\"criar()\">Criar</button>"
             + "</main>"
)
public class TurmaNovo {

    @NotBlank(message = "Nome e obrigatorio")
    public String nome = "";

    @NotBlank(message = "Ano letivo e obrigatorio")
    public String ano = "";

    public Map<String, String> errors = Map.of();

    private final TurmaService service;

    public TurmaNovo(TurmaService service) {
        this.service = service;
    }

    public void criar() {
        service.criar(nome, ano);
        throw new Redirect("/turmas");
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemAno() {
        return errors.getOrDefault("ano", "");
    }
}
