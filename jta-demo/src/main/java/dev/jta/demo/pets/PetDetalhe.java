package dev.jta.demo.pets;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import dev.jta.demo.visitas.Visita;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Path param + DI + fallback (mesmo padrao de {@code ProdutoDetalhe}),
 * mais um padrao novo no demo: uma acao que cria um registro FILHO
 * ({@link Visita}) a partir da propria pagina de detalhe do pai, sem
 * navegar para outra rota - {@code registrarVisita()} reidrata a lista de
 * visitas e limpa {@code dataVisita}/{@code descricaoVisita} para o
 * formulario aparecer vazio de novo apos o sucesso.
 */
@Route(value = "/pets/{id}", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "@if(self.encontrado())"
             + "<p><a href=\"/tutores/{{ tutorId() }}\">&larr; {{ nomeTutor() }}</a></p>"
             + "<h1>{{ nome() }}</h1>"
             + "<p>Especie: {{ especie() }}</p>"
             + "<p><a href=\"/pets/{{ id }}/editar\">Editar</a> &middot; "
             + "<button class=\"jta-btn-danger\" (click)=\"excluir()\">Excluir pet</button></p>"
             + "<h2>Visitas</h2>"
             + "<table class=\"jta-table\">"
             + "<tr><th>Data</th><th>Descricao</th></tr>"
             + "@for(var v : self.visitas())"
             + "<tr><td>${v.data()}</td><td>${v.descricao()}</td></tr>"
             + "@endfor"
             + "</table>"
             + "<h2>Nova visita</h2>"
             + "<div class=\"jta-field\"><label>Data</label>"
             + "<input type=\"date\" name=\"dataVisita\" value=\"{{ dataVisita }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemData() }}</p>"
             + "<div class=\"jta-field\"><label>Descricao</label>"
             + "<input type=\"text\" name=\"descricaoVisita\" value=\"{{ descricaoVisita }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemDescricao() }}</p>"
             + "<button (click)=\"registrarVisita()\">Registrar visita</button>"
             + "@else"
             + "<p>Pet nao encontrado.</p>"
             + "@endif"
             + "</main>"
)
public class PetDetalhe {

    public String id;

    @NotBlank(message = "Data e obrigatoria")
    public String dataVisita = "";

    @NotBlank(message = "Descricao e obrigatoria")
    public String descricaoVisita = "";

    public Map<String, String> errors = Map.of();

    private final PetService service;
    private Pet cache;
    private boolean buscado;

    public PetDetalhe(PetService service) {
        this.service = service;
    }

    private Pet pet() {
        if (!buscado) {
            cache = service.buscar(id).orElse(null);
            buscado = true;
        }
        return cache;
    }

    public boolean encontrado() {
        return pet() != null;
    }

    public String nome() {
        return pet().getNome();
    }

    public String especie() {
        return pet().getEspecie();
    }

    public String tutorId() {
        return pet().getTutor().getId();
    }

    public String nomeTutor() {
        return pet().getTutor().getNome();
    }

    public List<VisitaView> visitas() {
        return pet().getVisitas().stream().map(VisitaView::of).toList();
    }

    public void registrarVisita() {
        service.registrarVisita(id, dataVisita, descricaoVisita);
        // limpa o formulario - sem isso, o valor recem-submetido ficaria
        // reidratado (via bindableFields) na proxima renderizacao.
        dataVisita = "";
        descricaoVisita = "";
    }

    public void excluir() {
        String tutorId = tutorId();
        service.excluir(id);
        throw new Redirect("/tutores/" + tutorId);
    }

    public String mensagemData() {
        return errors.getOrDefault("dataVisita", "");
    }

    public String mensagemDescricao() {
        return errors.getOrDefault("descricaoVisita", "");
    }

    public record VisitaView(String data, String descricao) {
        static VisitaView of(Visita v) {
            return new VisitaView(v.getData(), v.getDescricao());
        }
    }
}
