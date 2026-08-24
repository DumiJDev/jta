package dev.jta.demo;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

import java.util.List;

/**
 * Exercita {@code @if}/{@code @for} (JTE puro, passa direto pelo
 * TemplateTransformer sem transformacao - ver decisao de design) e o
 * toggle de um campo boolean via acao. Deliberadamente um POJO simples
 * (sem {@code @Component}) para contrastar com {@code ProdutoCatalogo}/
 * {@code ProdutoDetalhe}: nem todo componente precisa de DI, e o
 * framework suporta os dois caminhos de instanciacao.
 */
@Route(value = "/tarefas", layout = SiteLayout.class)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<input type=\"hidden\" name=\"mostrarConcluidas\" value=\"{{ mostrarConcluidas }}\"/>"
             + "<h1>{{ titulo }}</h1>"
             + "<button (click)=\"alternar()\">Mostrar concluidas: {{ mostrarConcluidas }}</button>"
             + "<ul>"
             + "@for(var tarefa : self.pendentes)"
             + "<li>${tarefa}</li>"
             + "@endfor"
             + "@if(self.mostrarConcluidas)"
             + "<li><em>Escrever documento de arquitetura (concluida)</em></li>"
             + "@endif"
             + "</ul>"
             + "</main>"
)
public class TarefasComponent {
    public String titulo = "Tarefas do JTA";
    public boolean mostrarConcluidas = false;
    public List<String> pendentes = List.of(
            "Adicionar Jakarta Validation em cascata",
            "Prototipar Declarative Shadow DOM",
            "CLI de scaffolding"
    );

    public void alternar() {
        mostrarConcluidas = !mostrarConcluidas;
    }
}
