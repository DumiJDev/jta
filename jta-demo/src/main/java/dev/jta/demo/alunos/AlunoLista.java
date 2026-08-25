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
             + "@for(var aluno : self.alunos())"
             + "<div class=\"jta-card\">"
             + "<aluno-linha [id]=\"aluno.id\" [nome]=\"aluno.nome\" [email]=\"aluno.email\"/>"
             + "<button (click)=\"remover(aluno.id)\">Remover</button>"
             + "</div>"
             + "@endfor"
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

    /**
     * Prova de ponta a ponta de "argumentos em acoes" com raiz de
     * variavel de loop: {@code (click)="remover(aluno.id)"} dentro de
     * {@code @for(var aluno : self.alunos())} - o {@code id} do aluno
     * clicado chega aqui via {@code __jtaArg0}, resolvido por posicao
     * (nao por nome de parametro).
     */
    public void remover(String id) {
        service.excluir(id);
    }

    /**
     * Classe (nao record) DE PROPOSITO: a gramatica de raiz de variavel de
     * loop (compartilhada entre {@code [input]="aluno.campo"} e
     * {@code (click)="acao(aluno.campo)"}) so passa o acesso encadeado
     * verbatim para o Java gerado, sem validar alem da raiz - suporta
     * {@code aluno.campo} (campo publico) mas NAO suporta
     * {@code aluno.campo()} como argumento de acao (o parser de
     * argumentos e deliberadamente MVP: nao aceita parenteses aninhados
     * dentro de um argumento - ver {@code TemplateTransformer#EVENT_BINDING}).
     * Um record exporia {@code id()}/{@code nome()}/{@code email()}
     * (metodos, nao campos), que funcionaria em {@code [input]="..."} mas
     * quebraria silenciosamente em {@code (click)="remover(aluno.id())"}.
     */
    public static final class AlunoView {
        public final String id;
        public final String nome;
        public final String email;

        AlunoView(String id, String nome, String email) {
            this.id = id;
            this.nome = nome;
            this.email = email;
        }

        static AlunoView of(Aluno a) {
            return new AlunoView(a.getId(), a.getNome(), a.getEmail());
        }
    }
}
