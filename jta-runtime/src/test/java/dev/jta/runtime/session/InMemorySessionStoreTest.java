package dev.jta.runtime.session;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySessionStoreTest {

    @Test
    void getOrCreateComIdNuloGeraSessaoNova() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        JtaSession session = store.getOrCreate(null);
        assertNotNull(session.id());
    }

    @Test
    void getOrCreateComIdConhecidoDevolveAMesmaInstanciaEPreservaAtributos() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        JtaSession first = store.getOrCreate(null);
        first.setAttribute("contagem", 1);

        JtaSession second = store.getOrCreate(first.id());
        assertEquals(first.id(), second.id());
        assertEquals(1, second.attribute("contagem"));
    }

    @Test
    void getOrCreateComIdDesconhecidoGeraUmIdDiferente() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        JtaSession session = store.getOrCreate("id-que-nunca-existiu");
        assertNotEquals("id-que-nunca-existiu", session.id());
    }

    @Test
    void getDevolveVazioParaIdDesconhecidoOuNulo() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        assertTrue(store.get(null).isEmpty());
        assertTrue(store.get("desconhecido").isEmpty());
    }

    @Test
    void getDevolvePresenteParaSessaoExistente() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        JtaSession created = store.getOrCreate(null);

        Optional<JtaSession> found = store.get(created.id());
        assertTrue(found.isPresent());
        assertEquals(created.id(), found.get().id());
    }

    @Test
    void invalidateRemoveASessao() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        JtaSession session = store.getOrCreate(null);
        store.invalidate(session.id());
        assertTrue(store.get(session.id()).isEmpty());
    }

    @Test
    void sessaoInvalidaPeloProprioObjetoTambemDesaparaceDoStore() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        JtaSession session = store.getOrCreate(null);
        session.invalidate();
        assertTrue(store.get(session.id()).isEmpty());
    }

    @Test
    void sessaoExpiradaPorTtlEDescartadaLazilyNoProximoAcesso() throws InterruptedException {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMillis(10));
        JtaSession session = store.getOrCreate(null);
        Thread.sleep(50);
        assertTrue(store.get(session.id()).isEmpty());
    }

    @Test
    void setAttributeComValorNuloRemoveOAtributo() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        JtaSession session = store.getOrCreate(null);
        session.setAttribute("x", "valor");
        session.setAttribute("x", null);
        assertEquals(null, session.attribute("x"));
    }

    @Test
    void duasSessoesDiferentesNaoCompartilhamAtributos() {
        InMemorySessionStore store = new InMemorySessionStore(Duration.ofMinutes(30));
        JtaSession a = store.getOrCreate(null);
        JtaSession b = store.getOrCreate(null);
        a.setAttribute("contagem", 1);

        assertFalse(a.id().equals(b.id()));
        assertEquals(null, b.attribute("contagem"));
    }

    @Test
    void jtaSessionNoneENoOpTotal() {
        JtaSession none = JtaSession.none();
        none.setAttribute("x", "y");
        assertEquals(null, none.attribute("x"));
        assertEquals(null, none.id());
        none.removeAttribute("x");
        none.invalidate();
    }
}
