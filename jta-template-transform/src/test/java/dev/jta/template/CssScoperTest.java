package dev.jta.template;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CssScoperTest {

    @Test
    void prefixaSeletorTopLevelComAtributoDeEscopo() {
        String scoped = CssScoper.scope("h1 { color: green; }", "meu-cartao");

        assertEquals("[data-jta-component=\"meu-cartao\"] h1 { color: green; }", scoped);
    }

    @Test
    void prefixaCadaSeletorDeUmaListaSeparadaPorVirgula() {
        String scoped = CssScoper.scope("h1, h2 { margin: 0; }", "meu-cartao");

        assertEquals("[data-jta-component=\"meu-cartao\"] h1, [data-jta-component=\"meu-cartao\"] h2 { margin: 0; }",
                scoped);
    }

    @Test
    void cssVazioOuNuloViraStringVazia() {
        assertEquals("", CssScoper.scope("", "x"));
        assertEquals("", CssScoper.scope(null, "x"));
        assertEquals("", CssScoper.scope("   ", "x"));
    }

    @Test
    void regraDentroDeAtRuleEEscopadaMasOWrapperFicaLiteral() {
        // Comportamento real (nao o que o javadoc da classe resume): a regex
        // "([^{}@]+)\{([^{}]*)\}" nao reconhece o "@media (...) {" como parte
        // de uma regra (o '@' esta excluido do grupo de seletor), mas casa
        // normalmente a regra INTERNA "h1 { color: red; }" como se fosse
        // top-level - o wrapper do at-rule e que fica sem tratamento, nao a
        // regra em si. Documentado aqui como o comportamento real e
        // verificado (nao o resumo do javadoc, que descreve "o at-rule
        // inteiro e deixado como esta" de forma imprecisa) - preservado tal
        // como estava antes da extracao deste modulo, mudar isso e uma
        // decisao de design separada, nao parte desta extracao mecanica.
        String scoped = CssScoper.scope("@media (max-width: 600px) { h1 { color: red; } }", "meu-cartao");

        assertEquals("@media (max-width: 600px) {[data-jta-component=\"meu-cartao\"] h1 { color: red; } }", scoped);
    }
}
