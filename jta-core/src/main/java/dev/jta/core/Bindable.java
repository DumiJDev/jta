package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca explicitamente um campo publico como bindavel a partir de query
 * params/path variables/form data, mesmo que ele nunca apareca
 * literalmente como {@code {{ campo }}} em nenhum template.
 *
 * <p><b>Por que isso existe:</b> por padrao (sem esta anotacao), o JTA so
 * permite popular a partir de parametros externos os campos que o
 * template realmente referencia via {@code {{ }}} (mais os declarados
 * como {@code {param}} de rota) - "seguro por padrao" em vez de "todo
 * campo publico e bindavel" (que era mass assignment: qualquer campo
 * publico virava alvo de query string, mesmo um pensado como uso
 * interno). Ver SECURITY.md, achado #5.
 *
 * <p>Essa restricao cobre o caso comum (o campo aparece em algum
 * {@code {{ campo }}}, geralmente um input escondido para reenviar
 * estado), mas nao cobre um campo usado <em>so</em> dentro de uma
 * expressao JTE nativa ({@code @if}/{@code @for}, que passam direto sem
 * o JTA analisar) sem nunca ser interpolado em lugar nenhum - ex: um
 * campo de paginacao lido so dentro da logica de um metodo de template.
 * Para esses casos raros, {@code @Bindable} declara a intencao
 * explicitamente, em vez do campo simplesmente parar de funcionar de
 * forma confusa.
 *
 * <pre>{@code
 * @Bindable
 * public int pagina = 0; // nunca aparece como {{ pagina }}, so dentro de produtos()
 * }</pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Bindable {
}
