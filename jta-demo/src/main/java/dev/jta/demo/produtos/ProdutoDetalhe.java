package dev.jta.demo.produtos;

import dev.jta.core.AComponent;
import dev.jta.core.Redirect;
import dev.jta.core.Route;
import dev.jta.demo.SiteLayout;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Prova de ponta a ponta do path param: {@code {id}} em {@code @Route}
 * e validado em compile-time contra o campo publico {@code id} (ver
 * {@code JtaAnnotationProcessor#validateRoutePathParams}), extraido pelo
 * Spring MVC em runtime, e reidratado no campo antes do template
 * renderizar - sem nenhum {@code @PathVariable} escrito a mao.
 *
 * <p>Metodos de template chamam o servico sob demanda (nao ha hook de
 * "onInit" no framework ainda) - memoizado em {@link #produto()} para
 * nao repetir a busca a cada interpolacao no mesmo render.
 */
@Route(value = "/produtos/{id}", layout = SiteLayout.class)
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<p><a href=\"/produtos\">&larr; Catalogo</a></p>"
             + "<input type=\"hidden\" name=\"id\" value=\"{{ id }}\"/>"
             + "<h1>{{ nome() }}</h1>"
             + "<p>Preco: {{ precoFormatado() }}</p>"
             + "@if(self.encontrado())"
             + "<p><a href=\"/produtos/{{ id }}/editar\">Editar</a></p>"
             + "<button (click)=\"excluir()\">Excluir</button>"
             + "@endif"
             + "</main>"
)
public class ProdutoDetalhe {

    public String id;

    private final ProdutoService service;
    private Produto cache;
    private boolean buscado;

    public ProdutoDetalhe(ProdutoService service) {
        this.service = service;
    }

    private Produto produto() {
        if (!buscado) {
            cache = service.buscar(id).orElse(null);
            buscado = true;
        }
        return cache;
    }

    public boolean encontrado() {
        return produto() != null;
    }

    public String nome() {
        Produto p = produto();
        return p != null ? p.getNome() : "Produto '" + id + "' nao encontrado";
    }

    public String precoFormatado() {
        Produto p = produto();
        return p != null ? String.format(java.util.Locale.US, "R$ %.2f", p.getPreco()) : "-";
    }

    public void excluir() {
        service.excluir(id);
        throw new Redirect("/produtos");
    }
}
