package dev.jta.demo.vets;

import dev.jta.core.AComponent;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Listagem aberta (sem {@code @RequiresRole}) - so o cadastro/edicao
 * (ver {@code VeterinarioNovo}/{@code VeterinarioEditar}) e restrito a
 * ADMIN.
 */
@Route(value = "/veterinarios", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<h1>Veterinarios</h1>"
             + "<p><a href=\"/veterinarios/novo\">+ Novo veterinario</a> (requer login como administrador)</p>"
             + "<table class=\"jta-table\">"
             + "<tr><th>Nome</th><th>Especialidade</th><th></th></tr>"
             + "@for(var v : self.veterinarios())"
             + "<tr><td>${v.nome()}</td><td>${v.especialidade()}</td>"
             + "<td><a href=\"/veterinarios/${v.id()}/editar\">editar</a></td></tr>"
             + "@endfor"
             + "</table>"
             + "</main>"
)
public class VeterinarioLista {

    private final VeterinarioService service;

    public VeterinarioLista(VeterinarioService service) {
        this.service = service;
    }

    public List<VetView> veterinarios() {
        return service.listar().stream().map(VetView::of).toList();
    }

    public record VetView(String id, String nome, String especialidade) {
        static VetView of(Veterinario v) {
            return new VetView(v.getId(), v.getNome(), v.getEspecialidade());
        }
    }
}
