package dev.jta.template;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Testes unitarios para {@link TemplateTransformer}, cobrindo o
 * comportamento pre-existente (interpolacao, i18n, escopo de CSS) e a
 * nova sintaxe de composicao de componentes + argumentos em acoes.
 */
class TemplateTransformerTest {

    private static final Set<String> NO_FIELDS = Set.of();
    private static final Set<String> NO_METHODS = Set.of();
    private static final Map<String, List<String>> NO_ACTIONS = Map.of();
    private static final Set<String> NO_NULLABLE = Set.of();
    private static final Set<String> NO_MESSAGES = Set.of();
    private static final Map<String, TemplateTransformer.ChildRef> NO_CHILDREN = Map.of();

    // --- comportamento pre-existente, garantindo que a reescrita nao regrediu ---

    @Test
    void interpolacaoSimplesDeCampoFuncionaComoAntes() {
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div>{{ nome }}</div>", "meu-sel", Set.of("nome"), NO_METHODS, NO_ACTIONS, NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertFalse(result.hasErrors(), errorsOf(result));
        assertTrue(result.generatedJte().contains("${self.nome}"));
        assertEquals(List.of("nome"), result.referencedFields());
    }

    @Test
    void eventoSemArgumentosContinuaFuncionando() {
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<button (click)=\"incrementar()\">+</button>", "meu-sel", NO_FIELDS, NO_METHODS,
                Map.of("incrementar", List.of()), NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertFalse(result.hasErrors(), errorsOf(result));
        assertEquals(List.of("incrementar"), result.boundActions());
        assertTrue(result.generatedJte().contains("hx-post=\"/__jta/action/meu-sel?action=incrementar\""));
    }

    // --- @Use / composicao de componentes ---

    @Test
    void tagDeFilhoResolvidaGeraChamadaDeTemplateNativa() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of("titulo"), false, false);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><meu-card [titulo]=\"tituloDaLista\"/></div>", "pai-sel", Set.of("tituloDaLista"), NO_METHODS,
                NO_ACTIONS, NO_NULLABLE, NO_MESSAGES, Map.of("meu-card", child));

        assertFalse(result.hasErrors(), errorsOf(result));
        assertEquals(List.of("dev.jta.demo.Card"), result.children());
        String jte = result.generatedJte();
        assertTrue(jte.contains("@template.dev.jta.demo.Card("));
        assertTrue(jte.contains("__jtaInvoker.instantiateChild(dev.jta.demo.Card.class"));
        assertTrue(jte.contains("\"titulo\", (Object)(self.tituloDaLista)"));
        assertTrue(jte.contains(", __jtaInvoker)"));
    }

    @Test
    void bindingComChavesDuplasEAceitoComoAlternativaDeEscrita() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of("ativo"), false, false);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><meu-card [ativo]=\"{{ true }}\"/></div>", "pai-sel", NO_FIELDS, NO_METHODS,
                NO_ACTIONS, NO_NULLABLE, NO_MESSAGES, Map.of("meu-card", child));

        assertFalse(result.hasErrors(), errorsOf(result));
        assertTrue(result.generatedJte().contains("\"ativo\", (Object)(true)"));
    }

    @Test
    void tagDeFilhoNaoResolvidaEErroComDidYouMean() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of(), false, false);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><meu-crad/></div>", "pai-sel", NO_FIELDS, NO_METHODS, NO_ACTIONS, NO_NULLABLE, NO_MESSAGES,
                Map.of("meu-card", child));

        assertTrue(result.hasErrors());
        assertTrue(result.errors().get(0).message().contains("meu-crad"));
        assertTrue(result.errors().get(0).message().contains("meu-card"));
    }

    @Test
    void propertyBindingParaCampoNaoInputEErro() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of("titulo"), false, false);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><meu-card [naoExiste]=\"x\"/></div>", "pai-sel", Set.of("x"), NO_METHODS, NO_ACTIONS,
                NO_NULLABLE, NO_MESSAGES, Map.of("meu-card", child));

        assertTrue(result.hasErrors());
        assertEquals("unknown-input", result.errors().get(0).kind());
    }

    @Test
    void raizDeBindingInexistenteNoPaiEErro() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of("titulo"), false, false);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><meu-card [titulo]=\"naoExiste\"/></div>", "pai-sel", NO_FIELDS, NO_METHODS, NO_ACTIONS,
                NO_NULLABLE, NO_MESSAGES, Map.of("meu-card", child));

        assertTrue(result.hasErrors());
        assertEquals("input-binding-root", result.errors().get(0).kind());
    }

    @Test
    void filhoAninhadoDentroDeForUsaVariavelDeLoopNoInput() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Linha", "aluno-linha", Set.of("nome"), false, false);
        String template = "<div>@for(var aluno : self.alunos())<aluno-linha [nome]=\"aluno.nome\"/>@endfor</div>";
        TemplateTransformer.Result result = TemplateTransformer.transform(
                template, "pai-sel", Set.of(), Set.of("alunos"), NO_ACTIONS, NO_NULLABLE, NO_MESSAGES,
                Map.of("aluno-linha", child));

        assertFalse(result.hasErrors(), errorsOf(result));
        assertTrue(result.generatedJte().contains("\"nome\", (Object)(aluno.nome)"));
    }

    @Test
    void layoutUsadoComoFilhoEErro() {
        var layout = new TemplateTransformer.ChildRef("dev.jta.demo.SiteLayout", "site-layout", Set.of(), true, false);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><site-layout/></div>", "pai-sel", NO_FIELDS, NO_METHODS, NO_ACTIONS, NO_NULLABLE, NO_MESSAGES,
                Map.of("site-layout", layout));

        assertTrue(result.hasErrors());
        assertEquals("child-is-layout", result.errors().get(0).kind());
    }

    @Test
    void raizDoTemplateComoTagDeFilhoEErro() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of(), false, false);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<meu-card/>", "pai-sel", NO_FIELDS, NO_METHODS, NO_ACTIONS, NO_NULLABLE, NO_MESSAGES,
                Map.of("meu-card", child));

        assertTrue(result.hasErrors());
        assertEquals("root-is-child", result.errors().get(0).kind());
    }

    // --- slots (conteudo projetado via <tag>...</tag>) ---

    @Test
    void slotVazioDeclaraParametroDeContentESemFallback() {
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><slot/></div>", "meu-sel", NO_FIELDS, NO_METHODS, NO_ACTIONS, NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertFalse(result.hasErrors(), errorsOf(result));
        assertTrue(result.hasSlot());
        String jte = result.generatedJte();
        assertTrue(jte.contains("@if(__jtaSlotDefault != null)${__jtaSlotDefault}@else"));
        assertTrue(jte.contains("@endif"));
    }

    @Test
    void slotComFallbackTransformaInterpolacaoDoFallback() {
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><slot>{{ vazio }}</slot></div>", "meu-sel", Set.of("vazio"), NO_METHODS, NO_ACTIONS,
                NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertFalse(result.hasErrors(), errorsOf(result));
        assertTrue(result.generatedJte().contains("${self.vazio}"));
    }

    @Test
    void templateSemSlotNaoDeclaraHasSlot() {
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div>{{ x }}</div>", "meu-sel", Set.of("x"), NO_METHODS, NO_ACTIONS, NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertFalse(result.hasSlot());
    }

    @Test
    void tagFilhoComCorpoPassaConteudoComoSlotDefault() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of(), false, true);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><meu-card>{{ titulo }}</meu-card></div>", "pai-sel", Set.of("titulo"), NO_METHODS,
                NO_ACTIONS, NO_NULLABLE, NO_MESSAGES, Map.of("meu-card", child));

        assertFalse(result.hasErrors(), errorsOf(result));
        assertTrue(result.warnings().isEmpty(), "filho declara slot, nao deveria haver aviso");
        String jte = result.generatedJte();
        assertTrue(jte.contains("@template.dev.jta.demo.Card(self = "));
        assertTrue(jte.contains("__jtaInvoker = __jtaInvoker"));
        assertTrue(jte.contains("__jtaSlotDefault = @`"));
        assertTrue(jte.contains("${self.titulo}"), "interpolacao dentro do slot deve resolver contra o PAI");
    }

    @Test
    void tagFilhoComCorpoMasFilhoSemSlotGeraAviso() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of(), false, false);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><meu-card>texto</meu-card></div>", "pai-sel", NO_FIELDS, NO_METHODS,
                NO_ACTIONS, NO_NULLABLE, NO_MESSAGES, Map.of("meu-card", child));

        assertFalse(result.hasErrors(), errorsOf(result));
        assertEquals(1, result.warnings().size());
        assertEquals("unused-slot-content", result.warnings().get(0).kind());
    }

    @Test
    void tagFilhoAutoFechadaContinuaSemChamadaNomeada() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of(), false, true);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><meu-card/></div>", "pai-sel", NO_FIELDS, NO_METHODS, NO_ACTIONS, NO_NULLABLE, NO_MESSAGES,
                Map.of("meu-card", child));

        assertFalse(result.hasErrors(), errorsOf(result));
        assertTrue(result.warnings().isEmpty());
        assertTrue(result.generatedJte().contains(", __jtaInvoker)"),
                "sem conteudo de slot, a chamada permanece posicional - zero mudanca de comportamento previo");
    }

    @Test
    void eventBindingDentroDeConteudoDeSlotEErro() {
        var child = new TemplateTransformer.ChildRef("dev.jta.demo.Card", "meu-card", Set.of(), false, true);
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div><meu-card><button (click)=\"salvar()\">Salvar</button></meu-card></div>", "pai-sel",
                NO_FIELDS, NO_METHODS, Map.of("salvar", List.of()), NO_NULLABLE, NO_MESSAGES, Map.of("meu-card", child));

        assertTrue(result.hasErrors());
        assertEquals("slot-event-binding", result.errors().get(0).kind());
    }

    // --- argumentos em acoes ---

    @Test
    void acaoComArgumentoSelfELiteralGeraQueryStringComUrlEncoding() {
        String template = "<button (click)=\"remover(id, 'x&y', 42, true)\">Remover</button>";
        TemplateTransformer.Result result = TemplateTransformer.transform(
                template, "pai-sel", Set.of("id"), NO_METHODS,
                Map.of("remover", List.of("String", "String", "int", "boolean")), NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertFalse(result.hasErrors(), errorsOf(result));
        String jte = result.generatedJte();
        assertTrue(jte.contains("__jtaArg0=${dev.jta.core.UrlEncoding.encode(self.id)}"));
        assertTrue(jte.contains("__jtaArg1=x%26y")); // literal: url-encoded estaticamente, sem UrlEncoding.encode
        assertTrue(jte.contains("__jtaArg2=42"));
        assertTrue(jte.contains("__jtaArg3=true"));
    }

    @Test
    void acaoComArgumentoDeVariavelDeLoopResolveSemPrefixoSelf() {
        String template = "<div>@for(var aluno : self.alunos())"
                + "<button (click)=\"remover(aluno.id)\">Remover</button>@endfor</div>";
        TemplateTransformer.Result result = TemplateTransformer.transform(
                template, "pai-sel", Set.of(), Set.of("alunos"), Map.of("remover", List.of("String")),
                NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertFalse(result.hasErrors(), errorsOf(result));
        assertTrue(result.generatedJte().contains("__jtaArg0=${dev.jta.core.UrlEncoding.encode(aluno.id)}"));
    }

    @Test
    void aridadeIncompativelEErro() {
        String template = "<button (click)=\"remover(id)\">Remover</button>";
        TemplateTransformer.Result result = TemplateTransformer.transform(
                template, "pai-sel", Set.of("id"), NO_METHODS, Map.of("remover", List.of()), NO_NULLABLE,
                NO_MESSAGES, NO_CHILDREN);

        assertTrue(result.hasErrors());
        assertEquals("action-arity", result.errors().get(0).kind());
    }

    @Test
    void raizDeArgumentoNaoEncontradaEErro() {
        String template = "<button (click)=\"remover(naoExiste)\">Remover</button>";
        TemplateTransformer.Result result = TemplateTransformer.transform(
                template, "pai-sel", Set.of("id"), NO_METHODS, Map.of("remover", List.of("String")), NO_NULLABLE,
                NO_MESSAGES, NO_CHILDREN);

        assertTrue(result.hasErrors());
        assertEquals("action-arg-root", result.errors().get(0).kind());
    }

    @Test
    void variavelDeLoopComMesmoNomeDeCampoPublicoEErro() {
        String template = "@for(var id : self.ids())<span>{{ id }}</span>@endfor";
        TemplateTransformer.Result result = TemplateTransformer.transform(
                template, "sel", Set.of("id"), Set.of("ids"), NO_ACTIONS, NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertTrue(result.hasErrors());
        assertTrue(result.errors().stream().anyMatch(e -> e.kind().equals("loop-shadowing")));
    }

    // --- hx-include escopado por instancia ---

    @Test
    void hxIncludeUsaSeletorEscopadoPorInstanciaComExclusaoDeDoisNiveis() {
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<button (click)=\"salvar()\">Salvar</button>", "pai-sel", NO_FIELDS, NO_METHODS,
                Map.of("salvar", List.of()), NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertFalse(result.hasErrors(), errorsOf(result));
        assertTrue(result.generatedJte().contains(
                "hx-include=\"[data-jta-scope='${__jtaScope}'] :is(input,select,textarea)"
                        + ":not([data-jta-scope='${__jtaScope}'] [data-jta-scope] :is(input,select,textarea))\""));
    }

    @Test
    void raizDeclaraVariavelDeEscopoEAtributoDataJtaScope() {
        TemplateTransformer.Result result = TemplateTransformer.transform(
                "<div>{{ x }}</div>", "sel", Set.of("x"), NO_METHODS, NO_ACTIONS, NO_NULLABLE, NO_MESSAGES, NO_CHILDREN);

        assertFalse(result.hasErrors(), errorsOf(result));
        String jte = result.generatedJte();
        assertTrue(jte.contains("!{long __jtaScope = dev.jta.runtime.RenderScope.next();}"));
        assertTrue(jte.contains("data-jta-component=\"sel\""));
        assertTrue(jte.contains("data-jta-scope=\"${__jtaScope}\""));
    }

    // --- rastreador de escopo de @for ---

    @Test
    void scanLoopScopesDetectaLoopsAninhados() {
        String template = "@for(var a : self.as())X@for(var b : self.bs())Y@endfor Z@endfor";
        List<TemplateTransformer.LoopScope> scopes = TemplateTransformer.scanLoopScopes(template);

        assertEquals(2, scopes.size());
        TemplateTransformer.LoopScope outer = scopes.stream().filter(s -> s.varName().equals("a")).findFirst().orElseThrow();
        TemplateTransformer.LoopScope inner = scopes.stream().filter(s -> s.varName().equals("b")).findFirst().orElseThrow();
        assertTrue(outer.start() < inner.start());
        assertTrue(outer.end() > inner.end());
    }

    @Test
    void scanChildTagNamesEncontraTagsComHifenAutoFechadas() {
        Set<String> names = TemplateTransformer.scanChildTagNames("<div><meu-card/><router-outlet/><input/></div>");
        assertEquals(Set.of("meu-card", "router-outlet"), names);
    }

    private static String errorsOf(TemplateTransformer.Result result) {
        StringBuilder sb = new StringBuilder();
        for (TemplateTransformer.ValidationError e : result.errors()) {
            sb.append(e.kind()).append(": ").append(e.message()).append('\n');
        }
        return sb.toString();
    }
}
