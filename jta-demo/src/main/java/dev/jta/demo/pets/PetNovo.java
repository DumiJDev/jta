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

/**
 * Criacao aninhada: {@code tutorId} vem do path param de
 * {@code @Route("/tutores/{tutorId}/pets/novo")} (validado em
 * compile-time contra este campo publico, como qualquer path param), e e
 * resubmetido como campo oculto na acao {@code criar()} - o mesmo
 * mecanismo de "estado reenviado a cada acao" usado em todo o demo, agora
 * carregando uma FK. Padrao novo no demo: nenhum componente anterior
 * usava um path param so para popular uma relacao, sem exibi-lo.
 */
@Route(value = "/tutores/{tutorId}/pets/novo", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/tutores/{{ tutorId }}\">&larr; Voltar</a></p>"
             + "<h1>Novo pet</h1>"
             + "<input type=\"hidden\" name=\"tutorId\" value=\"{{ tutorId }}\"/>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Especie</label>"
             + "<input type=\"text\" name=\"especie\" value=\"{{ especie }}\" placeholder=\"Cachorro, gato, ...\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemEspecie() }}</p>"
             + "<button (click)=\"criar()\">Criar</button>"
             + "</main>"
)
public class PetNovo {

    public String tutorId;

    @NotBlank(message = "Nome e obrigatorio")
    public String nome = "";

    @NotBlank(message = "Especie e obrigatoria")
    public String especie = "";

    public Map<String, String> errors = Map.of();

    private final PetService service;

    public PetNovo(PetService service) {
        this.service = service;
    }

    public void criar() {
        Pet pet = service.criar(tutorId, nome, especie);
        throw new Redirect("/pets/" + pet.getId());
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemEspecie() {
        return errors.getOrDefault("especie", "");
    }
}
