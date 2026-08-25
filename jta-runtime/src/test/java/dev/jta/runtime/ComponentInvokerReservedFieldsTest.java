package dev.jta.runtime;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Segunda camada de defesa contra o mass-assignment que
 * {@link dev.jta.core.ReservedFieldNames} documenta: mesmo que
 * {@code bindableFields} contenha um nome reservado (o que nao deveria
 * acontecer, dado que {@code JtaAnnotationProcessor} ja o exclui em
 * compile-time - ver o teste correspondente em {@code smoke-test.sh}),
 * este runtime nunca o popula a partir da requisicao. Simula exatamente
 * esse cenario de "primeira camada falhou" passando o nome reservado
 * explicitamente em {@code bindableFields}, para provar que a segunda
 * camada segura sozinha.
 */
class ComponentInvokerReservedFieldsTest {

    public static final class ComponenteComFlash {
        public String flashSuccess = "valor-original-do-servidor";
        public String nome = "";
    }

    private final ComponentInvoker invoker = new ComponentInvoker(new ReflectionComponentFactory());

    @Test
    void flashSuccessNuncaEPopuladoAPartirDeParamsMesmoSeNaAllowlist() {
        ComponenteComFlash instance = new ComponenteComFlash();

        invoker.populateFromParams(instance,
                Map.of("flashSuccess", new String[]{"mensagem-forjada-pelo-cliente"}, "nome", new String[]{"Ana"}),
                Set.of("flashSuccess", "nome")); // simula bindableFields "vazado" com um nome reservado

        assertEquals("valor-original-do-servidor", instance.flashSuccess,
                "campo com nome reservado nao pode ser sobrescrito por query param/form data, "
                        + "mesmo que apareca (por erro) na allowlist de bindableFields");
        assertEquals("Ana", instance.nome, "campos normais continuam populados normalmente");
    }

    @Test
    void flashSuccessNuncaEPopuladoAPartirDePathVariablesMesmoSeNaAllowlist() {
        ComponenteComFlash instance = new ComponenteComFlash();

        invoker.populateFromPathVariables(instance,
                Map.of("flashSuccess", "mensagem-forjada-pelo-cliente"),
                Set.of("flashSuccess"));

        assertEquals("valor-original-do-servidor", instance.flashSuccess);
    }
}
