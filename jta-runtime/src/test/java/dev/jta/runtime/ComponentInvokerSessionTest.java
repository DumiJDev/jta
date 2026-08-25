package dev.jta.runtime;

import dev.jta.runtime.session.InMemorySessionStore;
import dev.jta.runtime.session.JtaSession;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Mesmo padrao exato de {@code applyErrors}: {@code applySession} popula um
 * campo publico opcional chamado {@code session} e e um no-op silencioso se
 * o componente nao declarar esse campo.
 */
class ComponentInvokerSessionTest {

    private final ComponentInvoker invoker = new ComponentInvoker(new ReflectionComponentFactory());

    public static final class ComSessao {
        public JtaSession session;
    }

    public static final class SemSessao {
        public String nome = "";
    }

    @Test
    void populaCampoSessionQuandoPresente() {
        ComSessao instance = new ComSessao();
        JtaSession session = new InMemorySessionStore(Duration.ofMinutes(30)).getOrCreate(null);

        invoker.applySession(instance, session);

        assertSame(session, instance.session);
    }

    @Test
    void noOpSilenciosoQuandoCampoAusente() {
        SemSessao instance = new SemSessao();
        JtaSession session = new InMemorySessionStore(Duration.ofMinutes(30)).getOrCreate(null);

        assertDoesNotThrow(() -> invoker.applySession(instance, session));
    }

    @Test
    void campoSessionNuncaEBindavelViaRequisicao() {
        // 'session' nao esta em bindableFields (o template nunca o referencia
        // via {{ }} neste fixture) - mesmo enviando um parametro chamado
        // 'session', populateFromParams nao deve tocar no campo.
        ComSessao instance = new ComSessao();
        JtaSession real = new InMemorySessionStore(Duration.ofMinutes(30)).getOrCreate(null);
        invoker.applySession(instance, real);

        invoker.populateFromParams(instance, Map.of("session", new String[]{"forjado"}), Set.of());

        assertSame(real, instance.session);
    }
}
