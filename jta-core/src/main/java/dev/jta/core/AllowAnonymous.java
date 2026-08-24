package dev.jta.core;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marca explicitamente um componente como acessivel sem autenticacao.
 * Existe principalmente para documentar a intencao (um leitor do codigo
 * nao precisa adivinhar se a ausencia de {@link RequiresRole} foi
 * proposital ou um esquecimento) - o comportamento sem nenhuma das duas
 * anotacoes ja e "sem restricao" por padrao.
 */
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface AllowAnonymous {
}
