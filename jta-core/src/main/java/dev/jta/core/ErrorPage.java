package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Registra o componente anotado como o renderizador de um status HTTP de
 * erro especifico (ex: 404, 500, 403) - ver {@code ComponentRegistry#errorPage}.
 *
 * <p>Usado junto de {@code @AComponent} (nunca sozinho), com os mesmos
 * campos publicos reservados {@code errorStatus}/{@code errorPath}/
 * {@code errorDetail} de {@link ReservedFieldNames} disponiveis para o
 * template mostrar detalhe do erro - populados pelo runtime
 * ({@code ComponentInvoker#applyErrorInfo}), nunca pelo cliente.
 *
 * <pre>{@code
 * @AComponent(template = "<div>Pagina '{{ errorPath }}' nao encontrada.</div>")
 * @ErrorPage(404)
 * public class NotFoundPage {
 *     public String errorPath;
 * }
 * }</pre>
 *
 * <p>Wiring em runtime e responsabilidade de cada adaptador - ver
 * {@code JtaErrorPageRenderer} (jta-runtime) e o adaptador Spring
 * ({@code JtaErrorController}/{@code JtaExceptionHandler}).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface ErrorPage {

    /** Status HTTP que este componente renderiza (ex: 404, 500, 403). */
    int value();
}
