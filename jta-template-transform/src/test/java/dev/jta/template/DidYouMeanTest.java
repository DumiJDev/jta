package dev.jta.template;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DidYouMeanTest {

    @Test
    void sugereOCandidatoMaisProximoDentroDoLimiteDeDistancia() {
        String suggestion = DidYouMean.suggest("nomee", Set.of("nome", "email", "id"));

        assertEquals(" (voce quis dizer 'nome'?)", suggestion);
    }

    @Test
    void naoSugereNadaQuandoNenhumCandidatoEProximoOSuficiente() {
        String suggestion = DidYouMean.suggest("xyz", Set.of("nomeCompleto", "enderecoDeEmail"));

        assertEquals("", suggestion);
    }

    @Test
    void naoSugereNadaComConjuntoVazio() {
        assertEquals("", DidYouMean.suggest("qualquer", Set.of()));
    }

    @Test
    void permiteMaisDistanciaParaReferenciasMaisLongas() {
        // limite e max(2, tamanho/2) - uma referencia longa com varios
        // caracteres errados ainda deve sugerir o candidato certo.
        String suggestion = DidYouMean.suggest("nomeCompletoo", Set.of("nomeCompleto"));

        assertTrue(suggestion.contains("nomeCompleto"));
    }
}
