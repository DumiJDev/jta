package dev.jta.demo.alunos;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

@Route(value = "/alunos/{id}/editar", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/alunos/{{ id }}\">&larr; Voltar</a></p>"
             + "<h1>Editar aluno</h1>"
             + "@if(self.encontrado())"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Email</label>"
             + "<input type=\"text\" name=\"email\" value=\"{{ email }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemEmail() }}</p>"
             + "<div class=\"jta-field\"><label>Data de nascimento</label>"
             + "<input type=\"date\" name=\"nascimento\" value=\"{{ nascimento }}\"/></div>"
             + "<button (click)=\"salvar()\">Salvar</button>"
             + "@else"
             + "<p>Aluno nao encontrado.</p>"
             + "@endif"
             + "</main>"
)
public class AlunoEditar {

    public String id;

    @NotBlank(message = "Nome e obrigatorio")
    public String nome;

    @NotBlank(message = "Email e obrigatorio")
    @Email(message = "Email invalido")
    public String email;

    public String nascimento;

    public Map<String, String> errors = Map.of();

    private final AlunoService service;
    private boolean existente = false;

    public AlunoEditar(AlunoService service) {
        this.service = service;
    }

    public void init() {
        if (nome == null) {
            service.buscar(id).ifPresent(a -> {
                nome = a.getNome();
                email = a.getEmail();
                nascimento = a.getNascimento();
                existente = true;
            });
        } else {
            existente = true;
        }
    }

    public boolean encontrado() {
        return existente;
    }

    public void salvar() {
        service.atualizar(id, nome, email, nascimento);
        throw new Redirect("/alunos/" + id);
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemEmail() {
        return errors.getOrDefault("email", "");
    }
}
