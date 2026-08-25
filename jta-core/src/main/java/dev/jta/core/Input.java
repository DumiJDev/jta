package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca um campo publico do FILHO como elegivel a receber property binding
 * de um componente pai via {@code [nome]="expr"} no template do pai.
 *
 * <p>Irma de {@link Bindable}, mas para uma direcao diferente de dados:
 * {@link Bindable} controla o que uma REQUISICAO HTTP externa (query
 * param/path variable/form data) pode popular; {@code @Input} controla o
 * que um componente PAI, em compile-time, pode passar para uma instancia
 * do filho que ele mesmo cria via composicao (nunca passa por uma
 * requisicao HTTP - o valor ja chega tipado, avaliado no processo do pai).
 *
 * <p>Sem {@code @Input} no filho, {@code [foo]="..."} no pai referenciando
 * {@code foo} e erro de compilacao (fail-closed) - o mesmo espirito de
 * "seguro por padrao" de {@link Bindable}, so que para a fronteira
 * pai-filho em vez da fronteira requisicao-componente.
 *
 * <pre>{@code
 * public class Card {
 *     @Input
 *     public String titulo;
 * }
 * }</pre>
 *
 * <p><b>Retencao {@code RUNTIME}, ao contrario de {@link Bindable}:</b>
 * {@code @Bindable} so e lido pelo annotation processor (compile-time,
 * via {@code Element}); {@code @Input} e TAMBEM verificado em runtime,
 * via reflection real, por {@code ComponentInvoker#instantiateChild}
 * (defesa em profundidade extra contra popular um campo que deixou de
 * ser {@code @Input} num jar desatualizado) - precisa sobreviver ate a
 * JVM em execucao, nao so ate o bytecode.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Input {
}
