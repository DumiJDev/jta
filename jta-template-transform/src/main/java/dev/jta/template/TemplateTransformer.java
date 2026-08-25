package dev.jta.template;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Transforma um template {@code .jta} em {@code .jte}.
 *
 * <p><b>Decisao de escopo deliberada:</b> esta e uma transformacao
 * <em>regex-based</em>, nao um parser de expressoes completo. Isso e
 * intencional (ver decisoes de design do projeto): a sintaxe JTA foi
 * desenhada para ficar o mais proxima possivel da sintaxe nativa do JTE
 * (ex: {@code @if}/{@code @for} sao JTE puro, passam direto sem
 * transformacao), entao o unico trabalho real do JTA e:
 * <ol>
 *   <li>Interpolacao curta {@code {{ campo }}} -> {@code ${self.campo}}</li>
 *   <li>Event binding {@code (evento)="metodo(args...)"} -> atributos HTMX</li>
 *   <li>Composicao de componentes filhos {@code <tag [input]="expr"/>} -> {@code @template.Filho(...)}</li>
 *   <li>Injetar o atributo de escopo de CSS/instancia na raiz do template</li>
 * </ol>
 * Expressoes compostas complexas dentro de {@code {{ }}} (ex: operadores
 * logicos, ternarios) NAO sao suportadas nesta fase - o dev deve expor um
 * metodo de template para logica composta. Isso e uma limitacao conhecida
 * e documentada, nao um bug: escrever um parser de expressoes completo foi
 * uma decisao explicita de ficar para uma fase futura.
 *
 * <p><b>Por que este modulo e nao jta-processor:</b> esta classe so
 * manipula {@code String}/regex - nunca toca {@code javax.lang.model},
 * {@code Filer} ou {@code Messager}. Vive em {@code jta-template-transform}
 * (modulo irmao, tambem zero-dependencias) para ser reutilizavel em
 * contextos que nao sao annotation processing (ex: um dev-loop com hot
 * reload) e para ser testada diretamente, sem o peso de um harness de
 * compile-testing sobre {@code JtaAnnotationProcessor}.
 */
public final class TemplateTransformer {

    // {{ campo }}, {{ campo? }}, {{ campo! }} ou {{ metodo() }} - identificador
    // simples ou acesso a um nivel (campo.sub), sem operadores, com sufixo
    // opcional de null-safety (? = seguro, ! = assert non-null)
    private static final Pattern INTERPOLATION =
            Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*(?:\\(\\))?)\\s*([?!]?)\\s*\\}\\}");

    // (click)="incrementar()" ou (click)="remover(aluno.id, 'x', 42)" - nome
    // do evento + metodo de acao com lista de argumentos separados por
    // virgula (sem parenteses/virgulas aninhados - MVP deliberado).
    private static final Pattern EVENT_BINDING =
            Pattern.compile("\\(([a-zA-Z]+)\\)=\"([a-zA-Z_][a-zA-Z0-9_]*)\\(([^()]*)\\)\"");

    // primeira tag de abertura do template, para injetar o atributo de escopo
    private static final Pattern FIRST_OPEN_TAG = Pattern.compile("^\\s*<([a-zA-Z][a-zA-Z0-9-]*)");

    // {{ 'chave' | translate }} - i18n com verificacao estatica da chave
    // contra messages.properties (ver JtaAnnotationProcessor#messageKeys)
    private static final Pattern TRANSLATE =
            Pattern.compile("\\{\\{\\s*'([^']+)'\\s*\\|\\s*translate\\s*\\}\\}");

    // <minha-tag [prop]="expr" .../> - tag de componente filho aninhado.
    // Restrito a tags com hifen (convencao de selector/custom-element) para
    // nunca colidir com elementos HTML nativos.
    private static final Pattern CHILD_TAG =
            Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*(?:-[a-zA-Z0-9]+)+)((?:\\s+[^<>]*?)?)\\s*/>");

    // <minha-tag [prop]="expr" ...>conteudo para o slot default</minha-tag> -
    // forma ABERTA (nao auto-fechada) de uma tag de componente filho, usada
    // para passar conteudo projetado (ver transformChildTagsWithBody). MVP
    // deliberado: so o slot default (sem [slot="nome"] para slots
    // nomeados), e sem suporte a um filho do MESMO nome aninhado dentro do
    // proprio corpo (o "(?:(?!</?\\1\\b).)*?" abaixo evita casar ate o
    // </tag> de um filho igual aninhado, mas nao suporta profundidade > 1).
    private static final Pattern CHILD_TAG_WITH_BODY =
            Pattern.compile("<([a-zA-Z][a-zA-Z0-9]*(?:-[a-zA-Z0-9]+)+)((?:\\s+[^<>]*?)?)\\s*>((?:(?!</?\\1\\b).)*?)</\\1\\s*>", Pattern.DOTALL);

    // <slot/>, <slot></slot> ou <slot>conteudo de fallback</slot> - marcador
    // de onde o conteudo projetado por um componente PAI entra no template
    // deste componente (ver CHILD_TAG_WITH_BODY). MVP deliberado: um unico
    // slot "default" por componente, sem slots nomeados nesta versao.
    private static final Pattern SLOT_TAG =
            Pattern.compile("<slot\\s*(?:/>|>([\\s\\S]*?)</slot\\s*>)");

    // [titulo]="expr" dentro do texto de atributos de uma CHILD_TAG
    private static final Pattern INPUT_BINDING = Pattern.compile("\\[([a-zA-Z_][a-zA-Z0-9_]*)]=\"([^\"]*)\"");

    // @for(var aluno : self.alunos()) ... @endfor - so precisamos do nome
    // da variavel de loop; o resto (tipo, colecao) e JTE puro e nao nos
    // interessa aqui.
    private static final Pattern FOR_START =
            Pattern.compile("@for\\s*\\(\\s*[\\w<>\\[\\],.\\s]+\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*:\\s*[^)]*\\)");
    private static final Pattern FOR_END = Pattern.compile("@endfor\\b");

    // raiz self (campo, campo.sub, metodo()) OU raiz de variavel de loop -
    // mesma gramatica de {{ }}, compartilhada com argumentos de acao e
    // property binding.
    private static final Pattern ROOT_EXPR =
            Pattern.compile("^([a-zA-Z_][a-zA-Z0-9_]*)((?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*)(\\(\\))?$");

    private static final Pattern LITERAL_STRING = Pattern.compile("^'([^']*)'$");
    private static final Pattern LITERAL_NUMBER = Pattern.compile("^-?\\d+(\\.\\d+)?$");
    private static final Pattern LITERAL_BOOLEAN = Pattern.compile("^(true|false)$");

    public record Result(String generatedJte, List<String> boundActions, List<ValidationError> errors,
                  List<String> referencedFields, List<String> children, boolean hasSlot,
                  List<ValidationError> warnings) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    public record ValidationError(String kind, String reference, String message) {
    }

    /**
     * Descreve um filho ja resolvido (por selector canonico ou alias
     * {@code @Use}) que pode ser referenciado no template do consumidor.
     *
     * @param hasSlot true se o template do filho declara {@code <slot/>} -
     *                so conhecido para filhos do MESMO modulo (ver
     *                {@code JtaAnnotationProcessor#toChildRef}); para um
     *                filho de outro modulo/jar cujo template nao pode ser
     *                relido em compile-time (ex: {@code templateUrl}
     *                externo), fica {@code false} por melhor esforco - o
     *                pior caso e um aviso "conteudo pode ser ignorado" que
     *                pode ser falso-positivo, nunca um erro de build.
     */
    public record ChildRef(String fqn, String canonicalSelector, Set<String> inputFields, boolean isLayout, boolean hasSlot) {
    }

    /** Uma regiao de texto onde {@code varName} e uma variavel de loop valida. */
    public record LoopScope(String varName, int start, int end) {
    }

    private TemplateTransformer() {
    }

    /**
     * Varre o template em busca de nomes de tag candidatos a componente
     * filho aninhado (qualquer tag auto-fechada com hifen no nome) - usado
     * pelo {@code JtaAnnotationProcessor} ANTES de chamar {@link #transform}
     * para saber quais tags precisa tentar resolver via {@code @Use}/selector
     * canonico do modulo.
     */
    public static Set<String> scanChildTagNames(String rawTemplate) {
        Set<String> names = new LinkedHashSet<>();
        Matcher m = CHILD_TAG.matcher(rawTemplate);
        while (m.find()) {
            names.add(m.group(1));
        }
        return names;
    }

    /**
     * {@code true} se {@code rawTemplate} declara {@code <slot/>} - usado
     * pelo {@code JtaAnnotationProcessor} para (1) decidir se o {@code .jte}
     * gerado deste componente precisa do {@code @param gg.jte.Content}
     * extra, e (2) "espiar" (sem processar de verdade) se um FILHO
     * conhecido no modulo declara slot, para o aviso de conteudo projetado
     * sem slot para recebe-lo (ver {@link ChildRef#hasSlot()}).
     */
    public static boolean scanHasSlot(String rawTemplate) {
        return SLOT_TAG.matcher(rawTemplate).find();
    }

    /**
     * Varre {@code @for(var X : ...)}/{@code @endfor} produzindo a lista de
     * escopos de variavel de loop (com pilha, suporta aninhamento).
     * Escopos nao fechados (sem {@code @endfor} correspondente) se
     * estendem ate o fim do template - permissivo, ja que um template
     * malformado quanto a isso vai quebrar na compilacao JTE de qualquer
     * forma com um erro proprio do JTE.
     */
    public static List<LoopScope> scanLoopScopes(String template) {
        List<LoopScope> result = new ArrayList<>();
        Deque<int[]> stack = new ArrayDeque<>(); // [startOffset], nome fica em paralelo
        Deque<String> nameStack = new ArrayDeque<>();

        record Token(int pos, boolean isStart, String varName) {
        }
        List<Token> tokens = new ArrayList<>();
        Matcher startM = FOR_START.matcher(template);
        while (startM.find()) {
            tokens.add(new Token(startM.start(), true, startM.group(1)));
        }
        Matcher endM = FOR_END.matcher(template);
        while (endM.find()) {
            tokens.add(new Token(endM.start(), false, null));
        }
        tokens.sort((a, b) -> Integer.compare(a.pos(), b.pos()));

        for (Token t : tokens) {
            if (t.isStart()) {
                stack.push(new int[]{t.pos()});
                nameStack.push(t.varName());
            } else if (!stack.isEmpty()) {
                int[] start = stack.pop();
                String name = nameStack.pop();
                result.add(new LoopScope(name, start[0], t.pos()));
            }
        }
        while (!stack.isEmpty()) {
            int[] start = stack.pop();
            String name = nameStack.pop();
            result.add(new LoopScope(name, start[0], template.length()));
        }
        return result;
    }

    private static boolean isInScope(LoopScope scope, int offset) {
        return offset >= scope.start() && offset < scope.end();
    }

    /**
     * @param rawTemplate   template .jta original
     * @param selector      seletor ja resolvido do componente (para o atributo de escopo e o endpoint de acao)
     * @param knownFields   nomes de campos publicos do componente (referenciaveis em {{ }})
     * @param knownMethods  nomes de metodos de template (publicos, sem args, com retorno) referenciaveis em {{ metodo() }}
     * @param knownActions  nome da acao -> aridade declarada (metodos publicos void referenciaveis em (evento)="acao(args)")
     * @param nullableFields subconjunto de knownFields anotado com algo chamado
     *                       "Nullable" (qualquer pacote - JSpecify, javax, jetbrains -
     *                       comparado pelo nome simples para nao forcar uma dependencia
     *                       especifica). Referenciar um desses campos com {{ campo }}
     *                       puro (sem sufixo) e erro de compilacao - precisa ser
     *                       {{ campo? }} (seguro) ou {{ campo! }} (assert non-null).
     * @param messageKeys   chaves conhecidas em {@code messages.properties} - toda
     *                      chave usada em {{ 'chave' | translate }} precisa existir
     *                      aqui, ou o build falha (i18n com verificacao estatica).
     * @param knownChildTags tags/aliases ja resolvidos pelo processor (selector
     *                       canonico ou {@code @Use}) para informacao do filho -
     *                       uma tag usada no template mas ausente daqui e erro
     *                       (com DidYouMean contra as chaves deste mapa).
     */
    public static Result transform(String rawTemplate, String selector,
                             Set<String> knownFields, Set<String> knownMethods, Map<String, List<String>> knownActions,
                             Set<String> nullableFields, Set<String> messageKeys,
                             Map<String, ChildRef> knownChildTags) {
        List<ValidationError> errors = new ArrayList<>();
        List<ValidationError> warnings = new ArrayList<>();
        List<String> boundActions = new ArrayList<>();
        List<String> children = new ArrayList<>();
        java.util.Set<String> referencedFields = new java.util.LinkedHashSet<>();

        validateRootIsNotChildTag(rawTemplate, knownChildTags, errors);
        validateLoopVariableShadowing(rawTemplate, knownFields, errors);

        boolean hasSlot = scanHasSlot(rawTemplate);

        String withScope = injectScopeAttribute(rawTemplate, selector);
        String withSlot = transformSlotTag(withScope);
        String withChildBodies = transformChildTagsWithBody(withSlot, knownFields, knownMethods, nullableFields,
                messageKeys, knownChildTags, errors, warnings, children);
        String withChildren = transformChildTags(withChildBodies, knownFields, knownMethods, knownChildTags, errors, children);
        String withEvents = transformEventBindings(withChildren, selector, knownActions, boundActions, errors, knownFields, knownMethods);
        String withTranslations = transformTranslations(withEvents, messageKeys, errors);
        String jte = transformInterpolations(withTranslations, knownFields, knownMethods, nullableFields, errors, referencedFields);

        validateNoStrayAtSigns(jte, errors);

        return new Result(jte, boundActions, errors, List.copyOf(referencedFields), List.copyOf(children), hasSlot, warnings);
    }

    /**
     * Substitui {@code <slot/>}/{@code <slot>fallback</slot>} por um
     * {@code @if/@else/@endif} do proprio JTE que escolhe entre o
     * {@code gg.jte.Content} passado pelo PAI ({@code __jtaSlotDefault},
     * um {@code @param} extra que {@code JtaAnnotationProcessor} so
     * adiciona ao cabecalho quando {@link #scanHasSlot} for {@code true} -
     * ver {@code jteHeader}) e o conteudo de fallback declarado aqui
     * (renderizado como o resto do template: as demais passagens desta
     * classe rodam DEPOIS desta, entao {{ }}/tags-filho dentro do fallback
     * continuam sendo transformados normalmente). Roda ANTES da injecao de
     * escopo interferir com outra coisa - na verdade roda logo depois dela,
     * sem nenhuma dependencia real de ordem com as passagens seguintes.
     */
    private static String transformSlotTag(String template) {
        Matcher m = SLOT_TAG.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String fallback = m.group(1) == null ? "" : m.group(1);
            String replacement = "@if(__jtaSlotDefault != null)${__jtaSlotDefault}@else "
                    + fallback + " @endif";
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static void validateRootIsNotChildTag(String rawTemplate, Map<String, ChildRef> knownChildTags,
                                                   List<ValidationError> errors) {
        Matcher m = FIRST_OPEN_TAG.matcher(rawTemplate);
        if (m.find() && knownChildTags.containsKey(m.group(1))) {
            errors.add(new ValidationError("root-is-child", m.group(1),
                    "a raiz do template nao pode ser diretamente a tag de um componente filho ('" + m.group(1)
                            + "') - o componente atual precisa da sua PROPRIA tag raiz para receber o atributo "
                            + "data-jta-component; envolva a tag do filho com um elemento proprio (ex: <div>...</div>)."));
        }
    }

    private static void validateLoopVariableShadowing(String rawTemplate, Set<String> knownFields,
                                                        List<ValidationError> errors) {
        for (LoopScope scope : scanLoopScopes(rawTemplate)) {
            if (knownFields.contains(scope.varName())) {
                errors.add(new ValidationError("loop-shadowing", scope.varName(),
                        "a variavel de loop '" + scope.varName() + "' declarada em @for(...) tem o MESMO nome de um "
                                + "campo publico do componente - isso e sombreamento ambiguo (fail-closed: renomeie "
                                + "a variavel de loop ou o campo)."));
            }
        }
    }

    // diretivas JTE reconhecidas - qualquer outro '@' no template e texto
    // comum que vai quebrar a compilacao do JTE com um erro interno confuso
    // (ex: "Missing @endfor" apontando pra um arquivo gerado que o dev nunca
    // escreveu). Ver JtaAnnotationProcessor/TROUBLESHOOTING.md.
    private static final Pattern KNOWN_JTE_DIRECTIVE = Pattern.compile(
            "@(if\\(|elseif\\(|else\\b|endif\\b|for\\(|endfor\\b|raw\\b|endraw\\b|import\\s|param\\s|template\\.|tag\\.|layout\\.|`)");

    private static void validateNoStrayAtSigns(String jte, List<ValidationError> errors) {
        int index = jte.indexOf('@');
        while (index >= 0) {
            String rest = jte.substring(index);
            if (!KNOWN_JTE_DIRECTIVE.matcher(rest).lookingAt()) {
                int contextStart = Math.max(0, index - 20);
                int contextEnd = Math.min(jte.length(), index + 20);
                String context = jte.substring(contextStart, contextEnd).replace('\n', ' ');
                errors.add(new ValidationError("stray-at-sign", "@",
                        "caractere '@' literal encontrado no template (perto de: \"..." + context + "...\"). "
                                + "O JTE trata '@' como inicio de uma diretiva mesmo dentro de texto comum (ex: um "
                                + "email escrito no HTML), o que quebra a compilacao com um erro confuso tipo "
                                + "'Missing @endfor' apontando para codigo gerado, nao para o seu template. Se for "
                                + "texto de verdade (nao uma diretiva JTE), envolva o trecho com @raw ... @endraw, "
                                + "ou reescreva o texto sem o caractere '@'."));
            }
            index = jte.indexOf('@', index + 1);
        }
    }

    /**
     * Injeta, na raiz do template: o atributo estatico de escopo de CSS
     * ({@code data-jta-component}) E uma declaracao de variavel local JTE
     * ({@code !{long __jtaScope = dev.jta.runtime.RenderScope.next();}})
     * mais o atributo {@code data-jta-scope} correspondente - usado para
     * escopar {@code hx-include} por instancia (ver {@link #buildHxInclude}).
     */
    private static String injectScopeAttribute(String template, String selector) {
        Matcher m = FIRST_OPEN_TAG.matcher(template);
        if (!m.find()) {
            // sem tag HTML na raiz (ex: fragmento so com {{ }}); nada a escopar
            return template;
        }
        int insertAt = m.end();
        String scopeDecl = "!{long __jtaScope = dev.jta.runtime.RenderScope.next();}";
        return scopeDecl
                + template.substring(0, insertAt)
                + " data-jta-component=\"" + selector + "\""
                + " data-jta-scope=\"${__jtaScope}\""
                + template.substring(insertAt);
    }

    /**
     * Seletor {@code hx-include} escopado por instancia, substituindo
     * {@code closest [data-jta-component]}.
     *
     * <p><b>Decisao real, verificada contra o htmx 4.0.0-beta6 de fato
     * pinado neste projeto</b> (lendo o bundle publicado, nao supondo):
     * a sintaxe originalmente cogitada
     * ({@code hx-include="closest [data-jta-component] :is(...):not(...)"})
     * NAO funciona - {@code "closest X"} passa X inteiro para
     * {@code Element.closest(X)}, que testa se um ANCESTRAL (ou o proprio
     * elemento) bate com X como selecionador composto completo, nunca
     * "descendente de X"; e o passo interno que de fato coleta os campos
     * dentro do container resolvido usa uma selecao HARDCODED
     * ({@code '[name]:not(button)'}), que nao pode ser customizada via o
     * valor do atributo {@code hx-include} de jeito nenhum.
     *
     * <p>A alternativa que realmente funciona: um seletor CSS PURO (sem
     * prefixo "closest "), resolvido via {@code document.querySelectorAll}
     * de verdade (suporta {@code :is()}/{@code :not()} com selecionadores
     * compostos - CSS Selectors Level 4), ancorado num atributo
     * {@code data-jta-scope} UNICO POR RENDER (nao por selector, que
     * repetiria em cada iteracao de um {@code @for} ou em instancias
     * irmãs do mesmo componente). A exclusao usa uma dupla-ancoragem
     * deliberada:
     * <pre>
     * [data-jta-scope='X'] :is(input,select,textarea)
     *   :not([data-jta-scope='X'] [data-jta-scope] :is(input,select,textarea))
     * </pre>
     * O primeiro {@code [data-jta-scope='X']} (repetido dentro do
     * {@code :not()}) ancora especificamente NESTA instancia; o segundo
     * {@code [data-jta-scope]} (sem valor) exige uma SEGUNDA fronteira
     * entre a instancia atual e o campo - exatamente "esta dentro de um
     * filho aninhado", em qualquer profundidade. Validado empiricamente
     * com jsdom contra este exato padrao (pai+filho+neto, cada um so
     * inclui os proprios campos).
     */
    private static String buildHxInclude() {
        return "[data-jta-scope='${__jtaScope}'] :is(input,select,textarea)"
                + ":not([data-jta-scope='${__jtaScope}'] [data-jta-scope] :is(input,select,textarea))";
    }

    private static String transformEventBindings(String template, String selector, Map<String, List<String>> knownActions,
                                                   List<String> boundActionsOut, List<ValidationError> errors,
                                                   Set<String> knownFields, Set<String> knownMethods) {
        List<LoopScope> loopScopes = scanLoopScopes(template);
        Matcher m = EVENT_BINDING.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String event = m.group(1);
            String action = m.group(2);
            String rawArgs = m.group(3).trim();
            int matchOffset = m.start();

            List<String> argExprs = splitArgs(rawArgs);

            if (!knownActions.containsKey(action)) {
                errors.add(new ValidationError("action", action,
                        "acao '" + action + "(...)' referenciada em (" + event + ") nao existe como metodo publico void "
                                + "no componente" + DidYouMean.suggest(action, knownActions.keySet())));
            } else {
                int declaredArity = knownActions.get(action).size();
                if (declaredArity != argExprs.size()) {
                    errors.add(new ValidationError("action-arity", action,
                            "acao '" + action + "' declara " + declaredArity + " parametro(s), mas foi chamada com "
                                    + argExprs.size() + " argumento(s) em (" + event + ")=\"" + action + "(" + rawArgs + ")\"."));
                }
                if (!boundActionsOut.contains(action)) {
                    boundActionsOut.add(action);
                }
            }

            StringBuilder queryString = new StringBuilder();
            for (int i = 0; i < argExprs.size(); i++) {
                String argExpr = argExprs.get(i).trim();
                String resolved = resolveArgExpression(argExpr, matchOffset, knownFields, knownMethods, loopScopes, errors);
                queryString.append("&__jtaArg").append(i).append("=").append(resolved);
            }

            String replacement = "hx-post=\"/__jta/action/" + selector + "?action=" + action + queryString + "\" "
                    + "hx-trigger=\"" + event + "\" "
                    + "hx-target=\"closest [data-jta-component]\" "
                    + "hx-swap=\"outerHTML\" "
                    + "hx-include=\"" + buildHxInclude() + "\"";
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Divide uma lista de argumentos separados por virgula no NIVEL
     * SUPERIOR apenas (MVP deliberado: sem suporte a virgula/parenteses
     * aninhados dentro de um argumento - ex: nao suporta passar o
     * resultado de outra chamada com argumentos).
     */
    private static List<String> splitArgs(String rawArgs) {
        if (rawArgs.isBlank()) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        for (String part : rawArgs.split(",", -1)) {
            parts.add(part.trim());
        }
        return parts;
    }

    /**
     * Resolve um argumento de acao (ou valor de property binding) para o
     * texto de expressao Java final a ser embutido no {@code .jte}
     * gerado, e decide se precisa ser envolvido em
     * {@code dev.jta.core.UrlEncoding.encode(...)} (toda expressao
     * DINAMICA, nunca um literal - ver {@code dev.jta.core.UrlEncoding}).
     * Usado apenas no contexto de query-string (argumentos de acao); para
     * inputs de property binding, ver {@link #resolveInputExpression}.
     */
    private static String resolveArgExpression(String rawExpr, int offset, Set<String> knownFields,
                                                Set<String> knownMethods, List<LoopScope> loopScopes,
                                                List<ValidationError> errors) {
        Literal literal = parseLiteral(rawExpr);
        if (literal != null) {
            return urlEncodeStatic(literal.text());
        }

        RootResolution resolution = resolveRoot(rawExpr, offset, knownFields, knownMethods, loopScopes);
        if (resolution == null) {
            errors.add(new ValidationError("action-arg-root", rawExpr,
                    "argumento de acao '" + rawExpr + "' nao e nem um literal ('texto'/42/true), nem um campo/metodo "
                            + "do componente (self.*), nem uma variavel de loop em escopo neste ponto do template"
                            + DidYouMean.suggest(rootOf(rawExpr), suggestionCandidates(knownFields, knownMethods, loopScopes, offset))));
            return "\"\"";
        }
        return "${dev.jta.core.UrlEncoding.encode(" + resolution.javaExpr() + ")}";
    }

    private static String urlEncodeStatic(String literalText) {
        return URLEncoder.encode(literalText, StandardCharsets.UTF_8);
    }

    private record Literal(String text) {
    }

    private static Literal parseLiteral(String expr) {
        Matcher strM = LITERAL_STRING.matcher(expr);
        if (strM.matches()) {
            return new Literal(strM.group(1));
        }
        if (LITERAL_NUMBER.matcher(expr).matches() || LITERAL_BOOLEAN.matcher(expr).matches()) {
            return new Literal(expr);
        }
        return null;
    }

    private record RootResolution(String javaExpr) {
    }

    private static String rootOf(String expr) {
        Matcher m = ROOT_EXPR.matcher(expr.trim());
        return m.matches() ? m.group(1) : expr;
    }

    private static Set<String> suggestionCandidates(Set<String> knownFields, Set<String> knownMethods,
                                                      List<LoopScope> loopScopes, int offset) {
        Set<String> candidates = new LinkedHashSet<>(knownFields);
        candidates.addAll(knownMethods);
        for (LoopScope scope : loopScopes) {
            if (isInScope(scope, offset)) {
                candidates.add(scope.varName());
            }
        }
        return candidates;
    }

    /**
     * Resolve a raiz de uma expressao ({@code self.*} ou variavel de loop)
     * contra o que esta valido NAQUELE PONTO do template. So a RAIZ e
     * validada contra os conjuntos conhecidos - o resto da expressao
     * (acesso encadeado, chamada de metodo) e repassado verbatim para o
     * Java gerado, exatamente como {{ campo.sub }} ja faz hoje para
     * campos de self (o proprio javac do modulo pega qualquer erro real
     * de tipo/membro na compilacao do .jte gerado).
     */
    private static RootResolution resolveRoot(String rawExpr, int offset, Set<String> knownFields,
                                               Set<String> knownMethods, List<LoopScope> loopScopes) {
        String expr = rawExpr.trim();
        Matcher m = ROOT_EXPR.matcher(expr);
        if (!m.matches()) {
            return null;
        }
        String root = m.group(1);
        boolean bareCall = m.group(2).isEmpty() && m.group(3) != null;

        boolean rootIsSelfField = knownFields.contains(root);
        boolean rootIsSelfMethod = knownMethods.contains(root);
        if (bareCall ? rootIsSelfMethod : rootIsSelfField) {
            return new RootResolution("self." + expr);
        }
        for (LoopScope scope : loopScopes) {
            if (scope.varName().equals(root) && isInScope(scope, offset)) {
                return new RootResolution(expr);
            }
        }
        return null;
    }

    private static String transformTranslations(String template, Set<String> messageKeys, List<ValidationError> errors) {
        Matcher m = TRANSLATE.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            if (!messageKeys.contains(key)) {
                errors.add(new ValidationError("i18n", key,
                        "chave de traducao '" + key + "' referenciada em {{ '" + key + "' | translate }} nao "
                                + "existe em messages.properties" + DidYouMean.suggest(key, messageKeys)));
            }
            String replacement = "${dev.jta.core.Translations.translate(\"" + key + "\")}";
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String transformInterpolations(String template, Set<String> knownFields, Set<String> knownMethods,
                                                    Set<String> nullableFields, List<ValidationError> errors,
                                                    Set<String> referencedFields) {
        Matcher m = INTERPOLATION.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String expr = m.group(1);
            String suffix = m.group(2); // "", "?" ou "!"
            boolean isMethodCall = expr.endsWith("()");
            boolean isDotted = expr.contains(".");
            String root = isMethodCall ? expr.substring(0, expr.length() - 2) : expr.split("\\.")[0];

            Set<String> validRoots = isMethodCall ? knownMethods : knownFields;
            if (!validRoots.contains(root)) {
                String kind = isMethodCall ? "metodo de template" : "campo";
                errors.add(new ValidationError(isMethodCall ? "method" : "field", root,
                        kind + " '" + root + "' referenciado em {{ " + expr + " }} nao existe no componente"
                                + DidYouMean.suggest(root, validRoots)));
                m.appendReplacement(out, Matcher.quoteReplacement("${self." + expr + "}"));
                continue;
            }

            if (!isMethodCall) {
                referencedFields.add(root);
            }

            boolean isNullable = !isMethodCall && nullableFields.contains(root);
            String replacement;

            if (isNullable && suffix.isEmpty()) {
                errors.add(new ValidationError("nullability", root,
                        "campo '" + root + "' e anotado @Nullable - referencia-lo com {{ " + expr + " }} sem "
                                + "sufixo e ambiguo sobre o que fazer se for null. Use {{ " + expr + "? }} "
                                + "(mostra vazio se for null) ou {{ " + expr + "! }} (assume que nao e null e "
                                + "quebra em runtime com uma mensagem clara se estiver errado)."));
                replacement = "${self." + expr + "}";
            } else if (isNullable && suffix.equals("?")) {
                if (isDotted) {
                    errors.add(new ValidationError("nullability", root,
                            "o operador '?' em {{ " + expr + "? }} so e suportado para campos de um unico nivel "
                                    + "nesta versao (sem '.'); acesso encadeado a um campo @Nullable precisa de "
                                    + "um metodo de template que faca a checagem de null na mao."));
                    replacement = "${self." + expr + "}";
                } else {
                    replacement = "${self." + expr + " == null ? \"\" : self." + expr + "}";
                }
            } else if (isNullable && suffix.equals("!")) {
                replacement = "${java.util.Objects.requireNonNull(self." + expr
                        + ", \"campo '" + expr + "' foi assertado non-null com {{ " + expr + "! }} mas era null em runtime\")}";
            } else {
                // campo nao-nullable, ou metodo: sufixo (se presente) e ignorado
                // silenciosamente - permissivo, nao e erro usar ? / ! onde nao faz falta.
                replacement = "${self." + expr + "}";
            }

            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    /**
     * Substitui cada tag de componente filho auto-fechada por uma chamada
     * de template JTE nativa ({@code @template.FilhoFqn(...)}), com o
     * filho instanciado inline via {@code __jtaInvoker.instantiateChild(...)} -
     * decisao ja tomada (nao string-splice pos-render como {@code @Layout}):
     * um filho aninhado dentro de {@code @for(...)} precisa ser composto
     * INLINE, no ponto exato da iteracao, que e exatamente o que
     * {@code @template.X(...)} do JTE resolve nativamente.
     */
    private static String transformChildTags(String template, Set<String> knownFields, Set<String> knownMethods,
                                              Map<String, ChildRef> knownChildTags, List<ValidationError> errors,
                                              List<String> childrenOut) {
        List<LoopScope> loopScopes = scanLoopScopes(template);
        Matcher m = CHILD_TAG.matcher(template);
        StringBuilder out = new StringBuilder();
        Set<String> seenChildren = new LinkedHashSet<>();
        while (m.find()) {
            String tagName = m.group(1);
            String attrText = m.group(2) == null ? "" : m.group(2);
            int offset = m.start();

            if ("router-outlet".equals(tagName)) {
                // marcador reservado de @Layout, substituido depois por
                // substituteRouterOutlet - nunca um componente filho.
                continue;
            }

            ChildRef child = knownChildTags.get(tagName);
            if (child == null) {
                errors.add(new ValidationError("child-tag", tagName,
                        "tag de componente filho '<" + tagName + "/>' nao resolvida - nao e um selector conhecido "
                                + "neste modulo nem um alias @Use declarado nesta classe"
                                + DidYouMean.suggest(tagName, knownChildTags.keySet())));
                m.appendReplacement(out, Matcher.quoteReplacement(""));
                continue;
            }
            if (child.isLayout()) {
                errors.add(new ValidationError("child-is-layout", tagName,
                        "'<" + tagName + "/>' resolve para " + child.fqn() + ", que e um @Layout - layouts sao "
                                + "exclusivos para compor pagina via <router-outlet/>, nao podem ser usados como "
                                + "componente filho aninhado."));
                m.appendReplacement(out, Matcher.quoteReplacement(""));
                continue;
            }

            Map<String, String> inputs = parseInputBindings(attrText, offset, child, tagName, knownFields, knownMethods, loopScopes, errors);

            childrenOut.add(child.fqn());
            seenChildren.add(child.fqn());

            String replacement = buildChildCallExpr(child, inputs, null);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static Map<String, String> parseInputBindings(String attrText, int offset, ChildRef child, String tagName,
                                                            Set<String> knownFields, Set<String> knownMethods,
                                                            List<LoopScope> loopScopes, List<ValidationError> errors) {
        Map<String, String> inputs = new LinkedHashMap<>();
        Matcher bindM = INPUT_BINDING.matcher(attrText);
        while (bindM.find()) {
            String inputName = bindM.group(1);
            String rawValue = bindM.group(2).trim();
            // aceita tanto a forma crua ("tituloDaLista") quanto envolvida
            // em chaves duplas ("{{ true }}") - mesma gramatica de raiz,
            // so uma variacao de escrita permitida por conveniencia.
            if (rawValue.startsWith("{{") && rawValue.endsWith("}}")) {
                rawValue = rawValue.substring(2, rawValue.length() - 2).trim();
            }

            if (!child.inputFields().contains(inputName)) {
                errors.add(new ValidationError("unknown-input", inputName,
                        "'[" + inputName + "]' em <" + tagName + "/> nao corresponde a nenhum campo @Input de "
                                + child.fqn() + DidYouMean.suggest(inputName, child.inputFields())));
                continue;
            }

            String javaExpr = resolveInputExpression(rawValue, offset, knownFields, knownMethods, loopScopes, errors, tagName, inputName);
            inputs.put(inputName, javaExpr);
        }
        return inputs;
    }

    /**
     * Monta a chamada {@code @template.FilhoFqn(...)} de composicao -
     * compartilhada entre a forma auto-fechada ({@link #transformChildTags})
     * e a forma com corpo/slot ({@link #transformChildTagsWithBody}).
     *
     * <p>Quando {@code slotContentExpr} e {@code null}, a chamada continua
     * inteiramente POSICIONAL (self, __jtaInvoker), exatamente como antes
     * desta feature - zero mudanca de comportamento/saida gerada para
     * quem nao usa slots. Só quando ha conteudo de slot a passar a chamada
     * vira NOMEADA (self = ..., __jtaInvoker = ..., __jtaSlotDefault = ...) -
     * a forma que o JTE exige para poder omitir/prover parametros com
     * default fora de ordem posicional.
     */
    private static String buildChildCallExpr(ChildRef child, Map<String, String> inputs, String slotContentExpr) {
        StringBuilder inputsJava = new StringBuilder();
        int i = 0;
        for (Map.Entry<String, String> e : inputs.entrySet()) {
            if (i > 0) {
                inputsJava.append(", ");
            }
            inputsJava.append("java.util.Map.entry(\"").append(e.getKey()).append("\", (Object)(").append(e.getValue()).append("))");
            i++;
        }
        String inputsMapExpr = inputs.isEmpty()
                ? "java.util.Map.<String,Object>of()"
                : "java.util.Map.<String,Object>ofEntries(" + inputsJava + ")";

        String instantiateExpr = "(" + child.fqn() + ") __jtaInvoker.instantiateChild(" + child.fqn() + ".class, " + inputsMapExpr + ")";

        if (slotContentExpr == null) {
            return "@template." + child.fqn() + "(" + instantiateExpr + ", __jtaInvoker)";
        }
        return "@template." + child.fqn() + "(self = " + instantiateExpr + ", __jtaInvoker = __jtaInvoker, "
                + "__jtaSlotDefault = " + slotContentExpr + ")";
    }

    /**
     * Substitui cada tag de componente filho em forma ABERTA
     * ({@code <tag>...</tag>}, ver {@link #CHILD_TAG_WITH_BODY}) pela
     * mesma chamada de composicao de {@link #transformChildTags}, mas
     * passando o corpo como conteudo de slot ({@code gg.jte.Content}, via
     * {@code @`...`}) - roda ANTES de {@link #transformChildTags} para que
     * a forma auto-fechada (sem {@code /} obrigatorio aqui) nao rouba
     * estas ocorrencias.
     *
     * <p>O corpo e recursivamente transformado (tags-filho aninhadas,
     * traducao, interpolacao) contra os campos/metodos/acoes do
     * consumidor (PAI) - conteudo projetado se comporta como se estivesse
     * escrito diretamente no template do pai, nao no do filho. Event
     * bindings ({@code (evento)="..."}) NAO sao suportados dentro de
     * conteudo de slot nesta versao (ver erro abaixo): o alvo de
     * {@code hx-target="closest [data-jta-component]"} gerado resolveria
     * para a raiz do FILHO (onde o conteudo e fisicamente renderizado no
     * DOM), nao a do pai que declara a acao - um bug de swap silencioso,
     * nao uma limitacao cosmetica.
     */
    private static String transformChildTagsWithBody(String template, Set<String> knownFields, Set<String> knownMethods,
                                                       Set<String> nullableFields, Set<String> messageKeys,
                                                       Map<String, ChildRef> knownChildTags, List<ValidationError> errors,
                                                       List<ValidationError> warnings, List<String> childrenOut) {
        List<LoopScope> loopScopes = scanLoopScopes(template);
        Matcher m = CHILD_TAG_WITH_BODY.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String tagName = m.group(1);
            String attrText = m.group(2) == null ? "" : m.group(2);
            String body = m.group(3) == null ? "" : m.group(3);
            int offset = m.start();

            if ("router-outlet".equals(tagName)) {
                continue; // deixa para substituteRouterOutlet - mesma excecao de transformChildTags
            }
            ChildRef child = knownChildTags.get(tagName);
            if (child == null) {
                errors.add(new ValidationError("child-tag", tagName,
                        "tag de componente filho '<" + tagName + ">...</" + tagName + ">' nao resolvida - nao e um "
                                + "selector conhecido neste modulo nem um alias @Use declarado nesta classe"
                                + DidYouMean.suggest(tagName, knownChildTags.keySet())));
                m.appendReplacement(out, Matcher.quoteReplacement(""));
                continue;
            }
            if (child.isLayout()) {
                errors.add(new ValidationError("child-is-layout", tagName,
                        "'<" + tagName + ">...</" + tagName + ">' resolve para " + child.fqn() + ", que e um "
                                + "@Layout - layouts nao podem ser usados como componente filho aninhado."));
                m.appendReplacement(out, Matcher.quoteReplacement(""));
                continue;
            }

            Map<String, String> inputs = parseInputBindings(attrText, offset, child, tagName, knownFields, knownMethods, loopScopes, errors);

            childrenOut.add(child.fqn());

            String slotContentExpr = null;
            if (!body.isBlank()) {
                if (EVENT_BINDING.matcher(body).find()) {
                    errors.add(new ValidationError("slot-event-binding", tagName,
                            "'<" + tagName + ">...</" + tagName + ">' - conteudo de slot nao pode conter "
                                    + "(evento)=\"...\": o alvo de swap resolveria para a raiz do componente FILHO, "
                                    + "nao a deste componente. Mova a acao para fora do slot, ou exponha um metodo "
                                    + "de template para a logica precisada aqui."));
                } else {
                    if (!child.hasSlot()) {
                        warnings.add(new ValidationError("unused-slot-content", tagName,
                                "'<" + tagName + ">...</" + tagName + ">' passa conteudo, mas " + child.fqn()
                                        + " nao declara nenhum <slot/> no seu template (ou o slot nao pode ser "
                                        + "confirmado em compile-time, se o filho vem de outro modulo) - este "
                                        + "conteudo pode ser ignorado em runtime."));
                    }
                    List<String> fragmentChildren = new ArrayList<>();
                    String fragment = transformChildTags(body, knownFields, knownMethods, knownChildTags, errors, fragmentChildren);
                    childrenOut.addAll(fragmentChildren);
                    fragment = transformTranslations(fragment, messageKeys, errors);
                    java.util.Set<String> ignoredRefs = new java.util.LinkedHashSet<>();
                    fragment = transformInterpolations(fragment, knownFields, knownMethods, nullableFields, errors, ignoredRefs);
                    slotContentExpr = "@`" + fragment + "`";
                }
            }

            String replacement = buildChildCallExpr(child, inputs, slotContentExpr);
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String resolveInputExpression(String rawValue, int offset, Set<String> knownFields,
                                                  Set<String> knownMethods, List<LoopScope> loopScopes,
                                                  List<ValidationError> errors, String tagName, String inputName) {
        Literal literal = parseLiteral(rawValue);
        if (literal != null) {
            // literal java: string entre aspas duplas, numero, ou boolean
            Matcher strM = LITERAL_STRING.matcher(rawValue);
            if (strM.matches()) {
                return "\"" + strM.group(1).replace("\"", "\\\"") + "\"";
            }
            return rawValue; // numero ou boolean: valido como literal Java tambem
        }
        RootResolution resolution = resolveRoot(rawValue, offset, knownFields, knownMethods, loopScopes);
        if (resolution == null) {
            errors.add(new ValidationError("input-binding-root", rawValue,
                    "raiz de '[" + inputName + "]=\"" + rawValue + "\"' em <" + tagName + "/> nao existe no "
                            + "componente pai (nem self.*, nem variavel de loop em escopo neste ponto)"
                            + DidYouMean.suggest(rootOf(rawValue), suggestionCandidates(knownFields, knownMethods, loopScopes, offset))));
            return "null";
        }
        return resolution.javaExpr();
    }
}
