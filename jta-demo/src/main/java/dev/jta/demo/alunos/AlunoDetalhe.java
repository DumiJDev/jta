package dev.jta.demo.alunos;

import dev.jta.core.AComponent;
import dev.jta.core.Bindable;
import dev.jta.core.Redirect;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import dev.jta.demo.matriculas.Matricula;
import dev.jta.demo.matriculas.MatriculaService;
import dev.jta.demo.notas.Nota;
import dev.jta.demo.notas.NotaService;
import dev.jta.demo.turmas.Turma;
import dev.jta.demo.turmas.TurmaRepository;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Matricula um aluno numa turma existente (criacao aninhada via FK
 * escolhida num select, nao path param) e mostra as notas ja lancadas -
 * o {@code id} do path so identifica o aluno "dono" da pagina, nunca e
 * exibido diretamente, so usado para carregar/filtrar as relacoes.
 */
@Route(value = "/alunos/{id}", layout = SiteLayout.class)
@RequiresRole("ADMIN")
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/alunos\">&larr; Alunos</a></p>"
             + "@if(self.encontrado())"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "<h1>{{ nome() }}</h1>"
             + "<p>Email: {{ email() }}</p>"
             + "<p><a href=\"/alunos/{{ id }}/editar\">Editar dados</a></p>"

             + "<h2>Turmas</h2>"
             + "<table class=\"jta-table\">"
             + "<tr><th>Turma</th><th>Ano</th></tr>"
             + "@for(var m : self.matriculas())"
             + "<tr><td>${m.getTurma().getNome()}</td><td>${m.getTurma().getAno()}</td></tr>"
             + "@endfor"
             + "</table>"
             + "<div class=\"jta-field\"><label>Matricular em nova turma</label>"
             + "<select name=\"turmaId\">"
             + "@for(var t : self.turmasDisponiveis())"
             + "<option value=\"${t.getId()}\">${t.getNome()} (${t.getAno()})</option>"
             + "@endfor"
             + "</select></div>"
             + "<button (click)=\"matricular()\">Matricular</button>"

             + "<h2>Notas</h2>"
             + "<p><a href=\"/notas/lancar/{{ id }}\">+ Lancar nota</a> (requer login como professor)</p>"
             + "<table class=\"jta-table\">"
             + "<tr><th>Disciplina</th><th>Nota</th></tr>"
             + "@for(var n : self.notas())"
             + "<tr><td>${n.getDisciplina().getNome()}</td><td>${n.getValor()}</td></tr>"
             + "@endfor"
             + "</table>"
             + "@else"
             + "<p>Aluno nao encontrado.</p>"
             + "@endif"
             + "</main>"
)
public class AlunoDetalhe {

    public String id;

    @Bindable
    public String turmaId = "";

    private final AlunoService alunoService;
    private final MatriculaService matriculaService;
    private final NotaService notaService;
    private final TurmaRepository turmaRepository;

    private Aluno cache;
    private boolean buscado;

    public AlunoDetalhe(AlunoService alunoService, MatriculaService matriculaService, NotaService notaService,
                         TurmaRepository turmaRepository) {
        this.alunoService = alunoService;
        this.matriculaService = matriculaService;
        this.notaService = notaService;
        this.turmaRepository = turmaRepository;
    }

    private Aluno aluno() {
        if (!buscado) {
            cache = alunoService.buscar(id).orElse(null);
            buscado = true;
        }
        return cache;
    }

    public boolean encontrado() {
        return aluno() != null;
    }

    public String nome() {
        return aluno().getNome();
    }

    public String email() {
        return aluno().getEmail();
    }

    public List<Matricula> matriculas() {
        return matriculaService.porAluno(id);
    }

    public List<Turma> turmasDisponiveis() {
        return turmaRepository.findAll();
    }

    public List<Nota> notas() {
        return notaService.porAluno(id);
    }

    public void matricular() {
        matriculaService.matricular(id, turmaId);
        throw new Redirect("/alunos/" + id);
    }
}
