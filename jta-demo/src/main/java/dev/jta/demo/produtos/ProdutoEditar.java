package dev.jta.demo.produtos;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Formulario de edicao - a primeira necessidade real de um hook de
 * inicializacao no framework ({@code init()}, ver
 * {@code JtaComponentInvoker#callInitIfPresent}): o formulario precisa
 * pre-carregar {@code nome}/{@code preco} do banco pelo {@code id} do
 * path ANTES do primeiro render, o que nem query params nem path
 * variables fazem sozinhos (eles so populam campos que o CLIENTE enviou).
 *
 * <p>{@code nome}/{@code preco} comecam como {@code null} (sentinela "nao
 * carregado ainda", nao {@code ""}/{@code 0}) para {@code init()} saber
 * se deve buscar no banco (GET inicial) ou se ja veio preenchido pelo
 * proprio usuario reenviando o formulario (POST de {@code salvar()}) -
 * sem isso, uma tentativa invalida perderia o que o usuario digitou e
 * mostraria de novo o valor antigo do banco.
 */
@Route(value = "/produtos/{id}/editar", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/produtos/{{ id }}\">&larr; Voltar</a></p>"
             + "<h1>Editar produto</h1>"
             + "@if(self.encontrado())"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "<div class=\"jta-field\"><label>Nome</label>"
             + "<input type=\"text\" name=\"nome\" value=\"{{ nome }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemNome() }}</p>"
             + "<div class=\"jta-field\"><label>Preco</label>"
             + "<input type=\"number\" step=\"0.01\" name=\"preco\" value=\"{{ preco }}\"/></div>"
             + "<p class=\"jta-error\">{{ mensagemPreco() }}</p>"
             + "<button (click)=\"salvar()\">Salvar</button>"
             + "@else"
             + "<p>Produto nao encontrado.</p>"
             + "@endif"
             + "</main>"
)
public class ProdutoEditar {

    public String id;

    @NotBlank(message = "Nome e obrigatorio")
    public String nome;

    @NotNull(message = "Preco e obrigatorio")
    @Positive(message = "Preco deve ser maior que zero")
    public Double preco;

    /** Populado por JtaActionController antes de renderizar - convencao, nao interface. */
    public Map<String, String> errors = Map.of();

    private final ProdutoService service;
    private boolean existente = false;

    public ProdutoEditar(ProdutoService service) {
        this.service = service;
    }

    public void init() {
        if (nome == null) {
            service.buscar(id).ifPresent(p -> {
                nome = p.getNome();
                preco = p.getPreco();
                existente = true;
            });
        } else {
            existente = true;
        }
    }

    public boolean encontrado() {
        return existente;
    }

    public void salvar() {
        service.atualizar(id, nome, preco);
        throw new Redirect("/produtos/" + id);
    }

    public String mensagemNome() {
        return errors.getOrDefault("nome", "");
    }

    public String mensagemPreco() {
        return errors.getOrDefault("preco", "");
    }
}
