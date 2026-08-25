package dev.jta.demo.alunos;

import dev.jta.core.AComponent;
import dev.jta.core.Input;

/**
 * Uma linha da lista de alunos, extraida de {@link AlunoLista} como
 * componente filho aninhado - prova de ponta a ponta de composicao de
 * componentes (property binding via {@code @Input}) e do isolamento de
 * {@code hx-include} entre pai e filho (ver {@code AlunoIntegrationTest}).
 *
 * <p>O campo {@code nome} tambem aparece como um {@code <input type="hidden">}
 * proprio (nao so texto) - deliberado, para dar ao teste de isolamento de
 * {@code hx-include} um campo de formulario real dentro da fronteira do
 * FILHO que a acao {@code remover(...)} do PAI (ver {@link AlunoLista})
 * nao pode vazar para a propria requisicao.
 */
@AComponent(
    selector = "aluno-linha",
    template = "<div class=\"jta-linha-aluno\">"
             + "<input type=\"hidden\" name=\"nome\" value=\"{{ nome }}\"/>"
             + "<a href=\"/alunos/${self.id}\">{{ nome }}</a> - {{ email }} "
             + "<a href=\"/alunos/${self.id}/editar\">editar</a>"
             + "</div>"
)
public class AlunoLinha {

    @Input
    public String id;

    @Input
    public String nome;

    @Input
    public String email;
}
