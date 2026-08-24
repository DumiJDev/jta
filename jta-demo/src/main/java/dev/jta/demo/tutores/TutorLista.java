package dev.jta.demo.tutores;

import dev.jta.core.AComponent;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Pagina inicial do CRUD de tutores (analoga a listagem de {@code Owner}
 * do Spring PetClinic) - mesma prova de DI real de {@code @Service} via
 * construtor que {@code ProdutoCatalogo} ja fazia, agora sobre uma
 * listagem em tabela.
 */
@Route(value = "/tutores", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<h1>Tutores</h1>"
             + "<p><a href=\"/tutores/novo\">+ Novo tutor</a></p>"
             + "<table class=\"jta-table\">"
             + "<tr><th>Nome</th><th>Telefone</th><th>Pets</th><th></th></tr>"
             + "@for(var t : self.tutores())"
             + "<tr>"
             + "<td><a href=\"/tutores/${t.id()}\">${t.nome()}</a></td>"
             + "<td>${t.telefone()}</td>"
             + "<td>${t.totalPets()}</td>"
             + "<td><a href=\"/tutores/${t.id()}/editar\">editar</a></td>"
             + "</tr>"
             + "@endfor"
             + "</table>"
             + "</main>"
)
public class TutorLista {

    private final TutorService service;

    public TutorLista(TutorService service) {
        this.service = service;
    }

    public List<TutorView> tutores() {
        return service.listar().stream().map(TutorView::of).toList();
    }

    /** Wrapper so para expor o total de pets sem carregar a colecao inteira no template. */
    public record TutorView(String id, String nome, String telefone, int totalPets) {
        static TutorView of(Tutor t) {
            return new TutorView(t.getId(), t.getNome(), t.getTelefone(), t.getPets().size());
        }
    }
}
