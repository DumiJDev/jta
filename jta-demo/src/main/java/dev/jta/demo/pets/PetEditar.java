package dev.jta.demo.pets;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

@Route(value = "/pets/{id}/editar", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/pets/{{ id }}\">&larr; Voltar</a></p>"
             + "<h1>Editar pet</h1>"
             + "@if(self.encontrado())"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Especie</label>"
             + "<input type=\"text\" name=\"especie\" value=\"{{ especie }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemEspecie() }}</p>"
             + "<button (click)=\"salvar()\">Salvar</button>"
             + "@else"
             + "<p>Pet nao encontrado.</p>"
             + "@endif"
             + "</main>"
)
public class PetEditar {

    public String id;

    @NotBlank(message = "Nome e obrigatorio")
    public String nome;

    @NotBlank(message = "Especie e obrigatoria")
    public String especie;

    public Map<String, String> errors = Map.of();

    private final PetService service;
    private boolean existente = false;

    public PetEditar(PetService service) {
        this.service = service;
    }

    public void init() {
        if (nome == null) {
            service.buscar(id).ifPresent(p -> {
                nome = p.getNome();
                especie = p.getEspecie();
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
        service.atualizar(id, nome, especie);
        throw new Redirect("/pets/" + id);
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemEspecie() {
        return errors.getOrDefault("especie", "");
    }
}
