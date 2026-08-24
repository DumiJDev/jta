package dev.jta.demo;

import dev.jta.core.AComponent;
import dev.jta.core.Route;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Demonstra Jakarta Validation (decisao de design: usar o padrao em vez de
 * anotacoes proprias). Constraints vao direto nos campos publicos - o
 * mesmo campo que carrega o estado da requisicao. Se houver violacoes,
 * {@code JtaActionController} nunca invoca {@code enviar()} - dados
 * invalidos simplesmente nao chegam ao codigo da acao.
 *
 * <p>O campo publico {@code errors} (convencao, nao interface) e populado
 * automaticamente pelo starter antes de cada render, com uma entrada por
 * campo que falhou. Metodos de template ({@code mensagemNome()}, etc.)
 * expoem o conteudo do mapa ao template, ja que {@code {{ }}} so suporta
 * acesso simples a campo/metodo - {@code {{ errors.nome }}} direto nao
 * compilaria (Map nao tem uma propriedade "nome").
 *
 * <p><b>Bug real corrigido:</b> antes os campos eram
 * {@code <input type="hidden">} com o valor mostrado como texto plano ao
 * lado - dava pra ver o estado, mas nunca dava pra DIGITAR nada. Agora
 * sao {@code <input type="text">} de verdade; o {@code hx-include}
 * (ja configurado por {@code TemplateTransformer}) inclui qualquer
 * input dentro do componente no POST da acao, entao a mudanca de
 * {@code hidden} para {@code text} nao exigiu nenhuma mudanca no
 * framework - so no HTML que o demo escrevia.
 */
@Route(value = "/contato", layout = SiteLayout.class)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<h1>Fale conosco</h1>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Email</label>"
             + "<input type=\"text\" name=\"email\" value=\"{{ email }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemEmail() }}</p>"
             + "<button (click)=\"enviar()\">Enviar</button>"
             + "<p class=\"jta-success\">{{ statusEnvio() }}</p>"
             + "</main>"
)
public class Contato {

    @NotBlank(message = "Nome e obrigatorio")
    public String nome = "";

    @NotBlank(message = "Email e obrigatorio")
    @Email(message = "Email invalido")
    public String email = "";

    /** Populado por JtaActionController antes de renderizar - convencao, nao interface. */
    public Map<String, String> errors = Map.of();

    private boolean enviado = false;

    public void enviar() {
        enviado = true;
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemEmail() {
        return errors.getOrDefault("email", "");
    }

    public String statusEnvio() {
        return enviado ? "Mensagem enviada, obrigado!" : "";
    }
}
