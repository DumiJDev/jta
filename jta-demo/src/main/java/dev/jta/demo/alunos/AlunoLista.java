package dev.jta.demo.alunos;

import dev.jta.core.AComponent;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Busca ao vivo sem nenhum JS proprio: o input dispara um GET HTMX para
 * esta mesma rota a cada tecla (com debounce), e {@code hx-select}/{@code hx-swap}
 * troca so a {@code <div id="alunos-resultado">} pelo trecho equivalente da
 * resposta - a pagina inteira continua sendo uma GET normal de
 * {@code @Route} (o mesmo dispatcher, sem endpoint de fragmento dedicado),
 * o HTMX que decide extrair so o pedaco relevante do HTML completo devolvido.
 * Estado (o termo buscado) e mantido pelo backend via query param, nao por
 * nenhuma variavel JS - o mesmo modelo de "estado gerenciado pelo backend"
 * usado em toda acao HTMX do JTA, so que aqui disparado por um GET em vez
 * de uma acao.
 */
@Route(value = "/alunos", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<h1>Alunos</h1>"
             + "<p><a href=\"/alunos/novo\">+ Novo aluno</a></p>"
             + "<div class=\"jta-field\">"
             + "<input type=\"text\" name=\"q\" value=\"{{ q }}\" placeholder=\"Pesquisar por nome...\" "
             + "hx-get=\"/alunos\" hx-trigger=\"keyup changed delay:300ms\" "
             + "hx-select=\"#alunos-resultado\" hx-target=\"#alunos-resultado\" hx-swap=\"outerHTML\"/>"
             + "</div>"
             + "<div id=\"alunos-resultado\">"
             + "<table class=\"jta-table\">"
             + "<tr><th>Nome</th><th>Email</th><th></th></tr>"
             + "@for(var a : self.alunos())"
             + "<tr><td><a href=\"/alunos/${a.id()}\">${a.nome()}</a></td><td>${a.email()}</td>"
             + "<td><a href=\"/alunos/${a.id()}/editar\">editar</a></td></tr>"
             + "@endfor"
             + "</table>"
             + "</div>"
             + "</main>"
)
public class AlunoLista {

    public String q = "";

    private final AlunoService service;

    public AlunoLista(AlunoService service) {
        this.service = service;
    }

    public List<AlunoView> alunos() {
        return service.listar(q).stream().map(AlunoView::of).toList();
    }

    public record AlunoView(String id, String nome, String email) {
        static AlunoView of(Aluno a) {
            return new AlunoView(a.getId(), a.getNome(), a.getEmail());
        }
    }
}
