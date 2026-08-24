package dev.jta.demo.notas;

import dev.jta.core.AComponent;
import dev.jta.core.Bindable;
import dev.jta.core.Redirect;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import dev.jta.demo.disciplinas.Disciplina;
import dev.jta.demo.disciplinas.DisciplinaRepository;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Restrito a PROFESSOR (nao ADMIN) - primeira pagina do demo com uma role
 * diferente de ADMIN, provando que {@code @RequiresRole} nao e so
 * "logado ou nao", e sim autorizacao por role de verdade.
 */
@Route(value = "/notas/lancar/{alunoId}", layout = SiteLayout.class)
@RequiresRole("PROFESSOR")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/alunos/{{ alunoId }}\">&larr; Voltar</a></p>"
             + "<h1>Lancar nota</h1>"
             + "<div class=\"jta-field\"><label>Disciplina</label>"
             + "<select name=\"disciplinaId\">"
             + "@for(var d : self.disciplinas())"
             + "<option value=\"${d.getId()}\">${d.getNome()}</option>"
             + "@endfor"
             + "</select></div>"
             + "<div class=\"jta-field\"><label>Nota (0 a 10)</label>"
             + "<input type=\"number\" step=\"0.1\" min=\"0\" max=\"10\" name=\"valor\" value=\"{{ valor }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemValor() }}</p>"
             + "<button (click)=\"lancar()\">Lancar nota</button>"
             + "</main>"
)
public class NotaLancar {

    public String alunoId;

    @Bindable
    public String disciplinaId = "";

    @DecimalMin(value = "0.0", message = "Nota minima e 0")
    @DecimalMax(value = "10.0", message = "Nota maxima e 10")
    public double valor;

    public Map<String, String> errors = Map.of();

    private final NotaService service;
    private final DisciplinaRepository disciplinaRepository;

    public NotaLancar(NotaService service, DisciplinaRepository disciplinaRepository) {
        this.service = service;
        this.disciplinaRepository = disciplinaRepository;
    }

    public List<Disciplina> disciplinas() {
        return disciplinaRepository.findAll();
    }

    public void lancar() {
        service.lancar(alunoId, disciplinaId, valor);
        throw new Redirect("/alunos/" + alunoId);
    }

    public String mensagemValor() {
        return errors.getOrDefault("valor", "");
    }
}
