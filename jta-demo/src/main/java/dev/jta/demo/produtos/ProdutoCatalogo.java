package dev.jta.demo.produtos;

import dev.jta.core.AComponent;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Pagina de catalogo com DI real de {@link ProdutoService} via
 * construtor - a mesma forma que qualquer {@code @RestController} do
 * Spring receberia um servico. {@code @Scope("prototype")} e
 * obrigatorio aqui: sem ele, o Spring devolveria a mesma instancia
 * singleton para toda requisicao (ver a trava de correcao em
 * {@code JtaComponentInvoker}).
 *
 * <p>{@code @for} no template e JTE puro (nao passa pelo
 * TemplateTransformer - so {@code {{ }}}/{@code ( )} sao transformados),
 * entao referencia {@code self} diretamente, como qualquer expressao Java
 * dentro de uma diretiva JTE.
 */
@Route(value = "/produtos", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<h1>Catalogo</h1>"
             + "<p><a href=\"/produtos/novo\">+ Novo produto</a></p>"
             + "@for(var p : self.produtos())"
             + "<div class=\"jta-card\">"
             + "<a href=\"/produtos/${p.id()}\"><strong>${p.nome()}</strong></a><br/>"
             + "<span>${p.precoFormatado()}</span> &middot; "
             + "<a href=\"/produtos/${p.id()}/editar\">editar</a>"
             + "</div>"
             + "@endfor"
             + "</main>"
)
public class ProdutoCatalogo {

    private final ProdutoService service;

    public ProdutoCatalogo(ProdutoService service) {
        this.service = service;
    }

    public java.util.List<ProdutoView> produtos() {
        return service.listar().stream().map(ProdutoView::of).toList();
    }

    /** Wrapper so para expor preco ja formatado ao template (JTE nao formata numeros por si). */
    public record ProdutoView(String id, String nome, String precoFormatado) {
        static ProdutoView of(Produto p) {
            return new ProdutoView(p.getId(), p.getNome(), String.format(java.util.Locale.US, "R$ %.2f", p.getPreco()));
        }
    }
}
