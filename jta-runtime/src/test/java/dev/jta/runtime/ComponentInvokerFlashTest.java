package dev.jta.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Mesmo padrao de no-op silencioso de {@code applySession}/{@code applyErrors}:
 * {@code applyFlash}/{@code applyErrorInfo} populam campos publicos
 * OPCIONAIS reservados (ver {@code dev.jta.core.ReservedFieldNames}) - o
 * componente so precisa declarar os que o template realmente usa.
 */
class ComponentInvokerFlashTest {

    private final ComponentInvoker invoker = new ComponentInvoker(new ReflectionComponentFactory());

    public static final class ComFlash {
        public String flashSuccess;
        public String flashError;
    }

    public static final class SemFlash {
        public String nome = "";
    }

    public static final class ComErrorInfo {
        public int errorStatus;
        public String errorPath;
        public String errorDetail;
    }

    @Test
    void populaFlashSuccessEFlashError() {
        ComFlash instance = new ComFlash();

        invoker.applyFlash(instance, "sucesso!", "erro!");

        org.junit.jupiter.api.Assertions.assertEquals("sucesso!", instance.flashSuccess);
        org.junit.jupiter.api.Assertions.assertEquals("erro!", instance.flashError);
    }

    @Test
    void flashNulaEValorValidoQuandoNaoHaFlashPendente() {
        ComFlash instance = new ComFlash();
        instance.flashSuccess = "algo antigo, nao deveria sobreviver";

        invoker.applyFlash(instance, null, null);

        assertNull(instance.flashSuccess);
    }

    @Test
    void componenteSemCamposDeFlashNaoQuebra() {
        SemFlash instance = new SemFlash();

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> invoker.applyFlash(instance, "x", "y"));
    }

    @Test
    void populaErrorStatusErrorPathErrorDetail() {
        ComErrorInfo instance = new ComErrorInfo();

        invoker.applyErrorInfo(instance, 404, "/rota-inexistente", "detalhe interno");

        org.junit.jupiter.api.Assertions.assertEquals(404, instance.errorStatus);
        org.junit.jupiter.api.Assertions.assertEquals("/rota-inexistente", instance.errorPath);
        org.junit.jupiter.api.Assertions.assertEquals("detalhe interno", instance.errorDetail);
    }
}
