package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca uma classe como um layout: um componente que envolve o conteudo
 * de uma pagina, com exatamente um marcador {@code <router-outlet/>} no
 * template indicando onde o conteudo da pagina entra.
 *
 * <p>Uma pagina usa um layout via {@code @Route(layout = MeuLayout.class)}.
 * A composicao acontece em runtime (nao em compile-time): a pagina e
 * renderizada primeiro, e o HTML resultante e passado como parametro
 * {@code content} para o template do layout - ver
 * {@code JtaRouteRegistrar} no starter.
 *
 * <pre>{@code
 * @Layout(template = "<nav>...</nav><router-outlet/>")
 * public class SiteLayout {}
 *
 * @Route(value = "/produtos", layout = SiteLayout.class)
 * @AComponent(template = "<h1>Catalogo</h1>...")
 * public class ProdutoCatalogo { ... }
 * }</pre>
 *
 * <p>Layouts aninhados (um layout usando outro layout) nao sao
 * suportados nesta versao - fica para uma fase futura se a demanda
 * aparecer.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Layout {

    /** Template inline, precisa conter exatamente um {@code <router-outlet/>}. */
    String template() default "";

    /** Caminho para um template externo (mesma convencao de {@link AComponent#templateUrl()}). */
    String templateUrl() default "";

    /** CSS inline, escopado automaticamente como qualquer outro componente. */
    String style() default "";

    /** Caminho para um CSS externo (mesma convencao de {@link AComponent#styleUrl()}). */
    String styleUrl() default "";
}
