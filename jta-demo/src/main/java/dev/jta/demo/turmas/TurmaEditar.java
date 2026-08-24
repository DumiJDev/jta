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

@Route(value = "/turmas/{id}/editar", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/turmas\">&larr; Voltar</a></p>"
             + "<h1>Editar turma</h1>"
             + "@if(self.encontrada())"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Ano letivo</label>"
             + "<input type=\"text\" name=\"ano\" value=\"{{ ano }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemAno() }}</p>"
             + "<button (click)=\"salvar()\">Salvar</button>"
             + "@else"
             + "<p>Turma nao encontrada.</p>"
             + "@endif"
             + "</main>"
)
public class TurmaEditar {

    public String id;

    @NotBlank(message = "Nome e obrigatorio")
    public String nome;

    @NotBlank(message = "Ano letivo e obrigatorio")
    public String ano;

    public Map<String, String> errors = Map.of();

    private final TurmaService service;
    private boolean existente = false;

    public TurmaEditar(TurmaService service) {
        this.service = service;
    }

    public void init() {
        if (nome == null) {
            service.buscar(id).ifPresent(t -> {
                nome = t.getNome();
                ano = t.getAno();
                existente = true;
            });
        } else {
            existente = true;
        }
    }

    public boolean encontrada() {
        return existente;
    }

    public void salvar() {
        service.atualizar(id, nome, ano);
        throw new Redirect("/turmas");
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemAno() {
        return errors.getOrDefault("ano", "");
    }
}
