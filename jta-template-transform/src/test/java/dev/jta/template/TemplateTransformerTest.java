package dev.jta.template;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobertura direta de {@link TemplateTransformer}, antes so validada
 * indiretamente via compilacao real de fixtures em {@code scripts/smoke-test.sh}
 * (que exercita o processor inteiro, nao so a transformacao). Escrita
 * durante a extracao desta classe para {@code jta-template-transform}, para
 * provar que a extracao preservou o comportamento exato.
 */
class TemplateTransformerTest {

    private static final Set<String> NO_METHODS = Set.of();
    private static final Set<String> NO_NULLABLE = Set.of();
    private static final Set<String> NO_MESSAGES = Set.of();

    @Test
    void interpolaCampoConhecidoParaExpressaoJte() {
        var result = TemplateTransformer.transform(
                "<p>{{ nome }}</p>", "meu-comp",
                Set.of("nome"), NO_METHODS, Set.of(), NO_NULLABLE, NO_MESSAGES);

        assertFalse(result.hasErrors());
        assertTrue(result.generatedJte().contains("${self.nome}"));
        assertEquals(java.util.List.of("nome"), result.referencedFields());
    }

    @Test
    void campoDesconhecidoEErroComSugestao() {
        var result = TemplateTransformer.transform(
                "<p>{{ nome }}</p>", "meu-comp",
                Set.of("nomee"), NO_METHODS, Set.of(), NO_NULLABLE, NO_MESSAGES);

        assertTrue(result.hasErrors());
        TemplateTransformer.ValidationError erro = result.errors().get(0);
        assertEquals("field", erro.kind());
        assertTrue(erro.message().contains("voce quis dizer 'nomee'?"),
                "erro de campo desconhecido deveria sugerir o campo real por proximidade: " + erro.message());
    }

    @Test
    void metodoDeTemplateUsaConjuntoDeMetodosNaoDeCampos() {
        var result = TemplateTransformer.transform(
                "<p>{{ saudacao() }}</p>", "meu-comp",
                Set.of(), Set.of("saudacao"), Set.of(), NO_NULLABLE, NO_MESSAGES);

        assertFalse(result.hasErrors());
        assertTrue(result.generatedJte().contains("${self.saudacao()}"));
        assertTrue(result.referencedFields().isEmpty(), "metodo de template nao e um campo bindavel");
    }

    @Test
    void eventBindingParaAcaoConhecidaGeraAtributosHtmx() {
        var result = TemplateTransformer.transform(
                "<button (click)=\"incrementar()\">+</button>", "contador",
                NO_METHODS, NO_METHODS, Set.of("incrementar"), NO_NULLABLE, NO_MESSAGES);

        assertFalse(result.hasErrors());
        assertEquals(java.util.List.of("incrementar"), result.boundActions());
        String jte = result.generatedJte();
        assertTrue(jte.contains("hx-post=\"/__jta/action/contador?action=incrementar\""));
        assertTrue(jte.contains("hx-target=\"closest [data-jta-component]\""));
        assertTrue(jte.contains("hx-swap=\"outerHTML\""));
    }

    @Test
    void acaoDesconhecidaEErro() {
        var result = TemplateTransformer.transform(
                "<button (click)=\"incrementarr()\">+</button>", "contador",
                NO_METHODS, NO_METHODS, Set.of("incrementar"), NO_NULLABLE, NO_MESSAGES);

        assertTrue(result.hasErrors());
        assertEquals("action", result.errors().get(0).kind());
    }

    @Test
    void primeiraTagAbertaGanhaAtributoDeEscopo() {
        var result = TemplateTransformer.transform(
                "<div><span>oi</span></div>", "meu-comp",
                Set.of(), NO_METHODS, Set.of(), NO_NULLABLE, NO_MESSAGES);

        assertTrue(result.generatedJte().startsWith("<div data-jta-component=\"meu-comp\">"));
    }

    @Test
    void templateSemTagRaizNaoRecebeEscopo() {
        var result = TemplateTransformer.transform(
                "{{ nome }}", "meu-comp",
                Set.of("nome"), NO_METHODS, Set.of(), NO_NULLABLE, NO_MESSAGES);

        assertFalse(result.generatedJte().contains("data-jta-component"));
    }

    @Test
    void chaveDeTraducaoConhecidaViraChamadaDeTranslations() {
        var result = TemplateTransformer.transform(
                "<p>{{ 'saudacao.ola' | translate }}</p>", "meu-comp",
                Set.of(), NO_METHODS, Set.of(), NO_NULLABLE, Set.of("saudacao.ola"));

        assertFalse(result.hasErrors());
        assertTrue(result.generatedJte().contains(
                "${dev.jta.core.Translations.translate(\"saudacao.ola\")}"));
    }

    @Test
    void chaveDeTraducaoDesconhecidaEErro() {
        var result = TemplateTransformer.transform(
                "<p>{{ 'saudacao.ola' | translate }}</p>", "meu-comp",
                Set.of(), NO_METHODS, Set.of(), NO_NULLABLE, Set.of());

        assertTrue(result.hasErrors());
        assertEquals("i18n", result.errors().get(0).kind());
    }

    @Test
    void campoNullableSemSufixoEErro() {
        var result = TemplateTransformer.transform(
                "<p>{{ apelido }}</p>", "meu-comp",
                Set.of("apelido"), NO_METHODS, Set.of(), Set.of("apelido"), NO_MESSAGES);

        assertTrue(result.hasErrors());
        assertEquals("nullability", result.errors().get(0).kind());
    }

    @Test
    void campoNullableComSufixoSeguroNaoEErro() {
        var result = TemplateTransformer.transform(
                "<p>{{ apelido? }}</p>", "meu-comp",
                Set.of("apelido"), NO_METHODS, Set.of(), Set.of("apelido"), NO_MESSAGES);

        assertFalse(result.hasErrors());
        assertTrue(result.generatedJte().contains("self.apelido == null ? \"\" : self.apelido"));
    }

    @Test
    void campoNullableComSufixoAssertNonNull() {
        var result = TemplateTransformer.transform(
                "<p>{{ apelido! }}</p>", "meu-comp",
                Set.of("apelido"), NO_METHODS, Set.of(), Set.of("apelido"), NO_MESSAGES);

        assertFalse(result.hasErrors());
        assertTrue(result.generatedJte().contains("java.util.Objects.requireNonNull(self.apelido"));
    }

    @Test
    void arrobaLiteralForaDeUmaDiretivaConhecidaEErro() {
        var result = TemplateTransformer.transform(
                "<p>Fale com a gente: contato@exemplo.com</p>", "meu-comp",
                Set.of(), NO_METHODS, Set.of(), NO_NULLABLE, NO_MESSAGES);

        assertTrue(result.hasErrors());
        assertEquals("stray-at-sign", result.errors().get(0).kind());
    }

    @Test
    void diretivasJteConhecidasPassamDiretoSemErro() {
        var result = TemplateTransformer.transform(
                "<ul>@for(var x : self.itens)<li>${x}</li>@endfor</ul>", "meu-comp",
                Set.of(), NO_METHODS, Set.of(), NO_NULLABLE, NO_MESSAGES);

        assertFalse(result.hasErrors());
    }
}
