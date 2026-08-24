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

/** DI + Jakarta Validation + persistencia + Redirect - mesmo padrao de {@code ProdutoNovo}. */
@Route(value = "/tutores/novo", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/tutores\">&larr; Tutores</a></p>"
             + "<h1>Novo tutor</h1>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Telefone</label>"
             + "<input type=\"text\" name=\"telefone\" value=\"{{ telefone }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemTelefone() }}</p>"
             + "<div class=\"jta-field\"><label>Endereco</label>"
             + "<input type=\"text\" name=\"endereco\" value=\"{{ endereco }}\"/></div>"
             + "<button (click)=\"criar()\">Criar</button>"
             + "</main>"
)
public class TutorNovo {

    @NotBlank(message = "Nome e obrigatorio")
    public String nome = "";

    @NotBlank(message = "Telefone e obrigatorio")
    public String telefone = "";

    public String endereco = "";

    /** Populado por JtaActionController antes de renderizar - convencao, nao interface. */
    public Map<String, String> errors = Map.of();

    private final TutorService service;

    public TutorNovo(TutorService service) {
        this.service = service;
    }

    public void criar() {
        Tutor tutor = service.criar(nome, telefone, endereco);
        throw new Redirect("/tutores/" + tutor.getId());
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemTelefone() {
        return errors.getOrDefault("telefone", "");
    }
}
