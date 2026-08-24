package dev.jta.demo.produtos;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Junta as tres features principais das rodadas anteriores num unico
 * componente: DI de {@link ProdutoService} via construtor, Jakarta
 * Validation bloqueando {@code criar()} com dados invalidos, e
 * persistencia real via JPA - o produto criado aqui aparece no catalogo
 * em {@link ProdutoCatalogo} porque os dois leem do mesmo banco H2.
 *
 * <p>Apos criar com sucesso, {@link Redirect} navega para a pagina de
 * detalhe do produto criado - o HTMX segue o header {@code HX-Redirect}
 * como uma navegacao de pagina inteira, em vez de deixar o usuario preso
 * na propria pagina do formulario.
 */
@Route(value = "/produtos/novo", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/produtos\">&larr; Catalogo</a></p>"
             + "<h1>Novo produto</h1>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Preco</label>"
             + "<input type=\"number\" step=\"0.01\" name=\"preco\" value=\"{{ preco }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemPreco() }}</p>"
             + "<button (click)=\"criar()\">Criar</button>"
             + "</main>"
)
public class ProdutoNovo {

    @NotBlank(message = "Nome e obrigatorio")
    public String nome = "";

    @Positive(message = "Preco deve ser maior que zero")
    public double preco = 0;

    /** Populado por JtaActionController antes de renderizar - convencao, nao interface. */
    public Map<String, String> errors = Map.of();

    private final ProdutoService service;

    public ProdutoNovo(ProdutoService service) {
        this.service = service;
    }

    public void criar() {
        Produto produto = service.criar(nome, preco);
        throw new Redirect("/produtos/" + produto.getId());
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemPreco() {
        return errors.getOrDefault("preco", "");
    }
}
