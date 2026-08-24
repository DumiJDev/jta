package dev.jta.demo.tutores;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

/** Usa {@code init()} para pre-carregar do banco - mesmo padrao de {@code ProdutoEditar}. */
@Route(value = "/tutores/{id}/editar", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/tutores/{{ id }}\">&larr; Voltar</a></p>"
             + "<h1>Editar tutor</h1>"
             + "@if(self.encontrado())"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Telefone</label>"
             + "<input type=\"text\" name=\"telefone\" value=\"{{ telefone }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemTelefone() }}</p>"
             + "<div class=\"jta-field\"><label>Endereco</label>"
             + "<input type=\"text\" name=\"endereco\" value=\"{{ endereco }}\"/></div>"
             + "<button (click)=\"salvar()\">Salvar</button>"
             + "@else"
             + "<p>Tutor nao encontrado.</p>"
             + "@endif"
             + "</main>"
)
public class TutorEditar {

    public String id;

    @NotBlank(message = "Nome e obrigatorio")
    public String nome;

    @NotBlank(message = "Telefone e obrigatorio")
    public String telefone;

    public String endereco;

    public Map<String, String> errors = Map.of();

    private final TutorService service;
    private boolean existente = false;

    public TutorEditar(TutorService service) {
        this.service = service;
    }

    public void init() {
        if (nome == null) {
            service.buscar(id).ifPresent(t -> {
                nome = t.getNome();
                telefone = t.getTelefone();
                endereco = t.getEndereco();
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
        service.atualizar(id, nome, telefone, endereco);
        throw new Redirect("/tutores/" + id);
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemTelefone() {
        return errors.getOrDefault("telefone", "");
    }
}
