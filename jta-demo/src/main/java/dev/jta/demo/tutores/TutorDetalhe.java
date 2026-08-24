package dev.jta.demo.tutores;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import dev.jta.demo.pets.Pet;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Path param + DI + fallback quando o id nao existe (mesma prova que
 * {@code ProdutoDetalhe} ja fazia), mais a listagem dos pets do tutor -
 * primeira relacao um-para-muitos exibida no demo.
 */
@Route(value = "/tutores/{id}", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/tutores\">&larr; Tutores</a></p>"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "<h1>{{ nome() }}</h1>"
             + "@if(self.encontrado())"
             + "<p>Telefone: {{ telefone() }} &middot; Endereco: {{ endereco() }}</p>"
             + "<p><a href=\"/tutores/{{ id }}/editar\">Editar</a> &middot; "
             + "<button class=\"jta-btn-danger\" (click)=\"excluir()\">Excluir tutor</button></p>"
             + "<h2>Pets</h2>"
             + "<p><a href=\"/tutores/{{ id }}/pets/novo\">+ Novo pet</a></p>"
             + "<table class=\"jta-table\">"
             + "<tr><th>Nome</th><th>Especie</th><th></th></tr>"
             + "@for(var p : self.pets())"
             + "<tr><td><a href=\"/pets/${p.id()}\">${p.nome()}</a></td><td>${p.especie()}</td>"
             + "<td><a href=\"/pets/${p.id()}/editar\">editar</a></td></tr>"
             + "@endfor"
             + "</table>"
             + "@endif"
             + "</main>"
)
public class TutorDetalhe {

    public String id;

    private final TutorService service;
    private Tutor cache;
    private boolean buscado;

    public TutorDetalhe(TutorService service) {
        this.service = service;
    }

    private Tutor tutor() {
        if (!buscado) {
            cache = service.buscar(id).orElse(null);
            buscado = true;
        }
        return cache;
    }

    public boolean encontrado() {
        return tutor() != null;
    }

    public String nome() {
        Tutor t = tutor();
        return t != null ? t.getNome() : "Tutor '" + id + "' nao encontrado";
    }

    public String telefone() {
        return tutor().getTelefone();
    }

    public String endereco() {
        return tutor().getEndereco();
    }

    public List<PetView> pets() {
        return tutor().getPets().stream().map(PetView::of).toList();
    }

    public void excluir() {
        service.excluir(id);
        throw new Redirect("/tutores");
    }

    public record PetView(String id, String nome, String especie) {
        static PetView of(Pet p) {
            return new PetView(p.getId(), p.getNome(), p.getEspecie());
        }
    }
}
