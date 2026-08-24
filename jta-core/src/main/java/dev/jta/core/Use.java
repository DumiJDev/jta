package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Target;

/**
 * Declara um alias local para um componente de terceiros, usado apenas
 * dentro do template do componente anotado.
 *
 * <p>Como o registro de seletores e global, um alias local e a forma de
 * resolver colisao entre dois componentes de bibliotecas distintas que
 * escolheram o mesmo seletor explicito - sem exigir que nenhuma das duas
 * bibliotecas mude. Equivalente, em espirito, a um import qualificado ou
 * alias de import no Java puro.
 *
 * <pre>{@code
 * @AComponent(
 *     template = "<libx-button>Enviar</libx-button>",
 * )
 * @Use(type = com.libx.Button.class, as = "libx-button")
 * public class Formulario { ... }
 * }</pre>
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
@Repeatable(Use.List.class)
public @interface Use {

    /** Classe do componente importado. */
    Class<?> type();

    /** Nome de tag a usar no template deste componente. */
    String as();

    @Retention(RetentionPolicy.CLASS)
    @Target(ElementType.TYPE)
    @interface List {
        Use[] value();
    }
}
