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

/**
 * Primeiro uso real de {@code @RequiresRole} no demo (validado contra
 * {@code AppRole} via {@code [security] roles_enum} em
 * {@code jta.config.toml}, e aplicado em runtime por
 * {@code JtaSecurityEnforcer}/{@code SecurityEnforcer} contra o usuario
 * autenticado por {@code SecurityConfig}).
 */
@Route(value = "/veterinarios/novo", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/veterinarios\">&larr; Veterinarios</a></p>"
             + "<h1>Novo veterinario</h1>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Especialidade</label>"
             + "<input type=\"text\" name=\"especialidade\" value=\"{{ especialidade }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemEspecialidade() }}</p>"
             + "<button (click)=\"criar()\">Criar</button>"
             + "</main>"
)
public class VeterinarioNovo {

    @NotBlank(message = "Nome e obrigatorio")
    public String nome = "";

    @NotBlank(message = "Especialidade e obrigatoria")
    public String especialidade = "";

    public Map<String, String> errors = Map.of();

    private final VeterinarioService service;

    public VeterinarioNovo(VeterinarioService service) {
        this.service = service;
    }

    public void criar() {
        service.criar(nome, especialidade);
        throw new Redirect("/veterinarios");
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemEspecialidade() {
        return errors.getOrDefault("especialidade", "");
    }
}
