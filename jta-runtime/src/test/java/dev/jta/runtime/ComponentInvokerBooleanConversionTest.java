package dev.jta.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regressao do bug real encontrado na auditoria: {@code Boolean.parseBoolean}
 * so reconhece a string literal {@code "true"}, mas um
 * {@code <input type="checkbox">} sem atributo {@code value} explicito - o
 * caso mais comum em HTML - envia {@code "on"} quando marcado. Um checkbox
 * ficava silenciosamente {@code false} por mais que o utilizador o
 * marcasse, sem erro nem log.
 */
class ComponentInvokerBooleanConversionTest {

    public static final class ComComCampoBooleano {
        public boolean ativo = false;
    }

    private final ComponentInvoker invoker = new ComponentInvoker(new ReflectionComponentFactory());

    @ParameterizedTest
    @ValueSource(strings = {"on", "true", "TRUE", "True", "yes", "YES", "1"})
    void valoresQueUmBrowserOuClienteEnviamParaMarcadoConvertemParaTrue(String rawValue) {
        ComComCampoBooleano instance = new ComComCampoBooleano();

        invoker.populateFromParams(instance, Map.of("ativo", new String[]{rawValue}), Set.of("ativo"));

        assertTrue(instance.ativo, "'" + rawValue + "' deveria converter para true");
    }

    @ParameterizedTest
    @ValueSource(strings = {"off", "false", "0", "no", ""})
    void valoresQueNaoSignificamMarcadoConvertemParaFalse(String rawValue) {
        ComComCampoBooleano instance = new ComComCampoBooleano();
        instance.ativo = true; // parte de true para provar que o valor realmente muda para false

        invoker.populateFromParams(instance, Map.of("ativo", new String[]{rawValue}), Set.of("ativo"));

        assertFalse(instance.ativo, "'" + rawValue + "' deveria converter para false");
    }

    @Test
    void parametroAusenteMantemOValorDefaultDoCampo() {
        ComComCampoBooleano instance = new ComComCampoBooleano();
        instance.ativo = true;

        // checkbox desmarcado: o browser simplesmente nao envia o parametro.
        invoker.populateFromParams(instance, Map.of(), Set.of("ativo"));

        assertTrue(instance.ativo, "sem o parametro na requisicao, o campo deve manter o valor atual - "
                + "ausencia nao e o mesmo que 'desmarcado enviado como false'");
    }
}
