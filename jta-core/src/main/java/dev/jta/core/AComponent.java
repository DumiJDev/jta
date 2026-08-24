package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca uma classe como um componente JTA.
 *
 * <p>Um componente combina um template (HTML com bindings), um view-model
 * (campos publicos) e acoes (metodos {@code void} invocados via HTMX).
 *
 * <p>O seletor do componente e sempre resolvido em compile-time. Se
 * {@link #selector()} for deixado em branco (padrao), o processor deriva um
 * seletor canonico a partir do nome totalmente qualificado da classe -
 * garantindo unicidade global sem exigir coordenacao entre bibliotecas.
 * Um seletor explicito e permitido para nomes curtos e legiveis, mas e
 * validado contra todo o classpath em build-time; colisoes falham o build
 * (ver {@link Use} para resolver colisoes via alias local).
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AComponent {

    /**
     * Template inline. Mutuamente exclusivo com {@link #templateUrl()}.
     */
    String template() default "";

    /**
     * Caminho para um arquivo de template externo (extensao {@code .jta}),
     * relativo a {@code src/main/resources/jta-templates/<pacote-do-componente>/}.
     * Ex: para {@code dev.acme.ui.Botao}, o arquivo
     * {@code templateUrl = "Botao.jta"} deve viver em
     * {@code src/main/resources/jta-templates/dev/acme/ui/Botao.jta}.
     * Mutuamente exclusivo com {@link #template()}.
     */
    String templateUrl() default "";

    /**
     * CSS inline, escopado automaticamente ao componente via
     * {@code [data-jta-component="<selector>"]}. Mutuamente exclusivo com
     * {@link #styleUrl()}.
     */
    String style() default "";

    /**
     * Caminho para um arquivo CSS externo, relativo a
     * {@code src/main/resources/jta-templates/<pacote-do-componente>/}
     * (mesma convencao de {@link #templateUrl()}). Mutuamente exclusivo
     * com {@link #style()}.
     */
    String styleUrl() default "";

    /**
     * Seletor explicito e opcional. Em branco = derivado do FQN da classe
     * (recomendado; nunca colide). Preenchido = precisa de unicidade
     * validada em compile-time em todo o classpath.
     */
    String selector() default "";
}
