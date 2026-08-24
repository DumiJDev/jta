package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Transforma um {@link AComponent} numa pagina roteavel, registrando
 * automaticamente um endpoint GET no framework hospedeiro.
 *
 * <p>Suporta parametros de rota simples, ex: {@code @Route("/produto/{id}")},
 * que sao injetados nos campos publicos do componente com o mesmo nome
 * (a conversao de tipo e validada em compile-time).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface Route {

    /** Caminho da rota, ex: {@code "/"}, {@code "/produto/{id}"}. */
    String value();

    /**
     * Layout que envolve esta pagina - uma classe anotada com
     * {@link Layout}. Deixe em branco (o padrao, {@code Void.class}) para
     * a pagina nao usar nenhum layout.
     */
    Class<?> layout() default Void.class;
}
