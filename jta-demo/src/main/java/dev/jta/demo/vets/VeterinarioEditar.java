package dev.jta.demo.vets;

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

@Route(value = "/veterinarios/{id}/editar", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/veterinarios\">&larr; Voltar</a></p>"
             + "<h1>Editar veterinario</h1>"
             + "@if(self.encontrado())"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Especialidade</label>"
             + "<input type=\"text\" name=\"especialidade\" value=\"{{ especialidade }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemEspecialidade() }}</p>"
             + "<button (click)=\"salvar()\">Salvar</button>"
             + "@else"
             + "<p>Veterinario nao encontrado.</p>"
             + "@endif"
             + "</main>"
)
public class VeterinarioEditar {

    public String id;

    @NotBlank(message = "Nome e obrigatorio")
    public String nome;

    @NotBlank(message = "Especialidade e obrigatoria")
    public String especialidade;

    public Map<String, String> errors = Map.of();

    private final VeterinarioService service;
    private boolean existente = false;

    public VeterinarioEditar(VeterinarioService service) {
        this.service = service;
    }

    public void init() {
        if (nome == null) {
            service.buscar(id).ifPresent(v -> {
                nome = v.getNome();
                especialidade = v.getEspecialidade();
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
        service.atualizar(id, nome, especialidade);
        throw new Redirect("/veterinarios");
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemEspecialidade() {
        return errors.getOrDefault("especialidade", "");
    }
}
