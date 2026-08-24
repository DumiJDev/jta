package dev.jta.processor;

import java.util.ArrayList;
import java.util.List;
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
 *   <li>Event binding {@code (evento)="metodo()"} -> atributos HTMX</li>
 *   <li>Injetar o atributo de escopo de CSS na raiz do template</li>
 * </ol>
 * Expressoes compostas complexas dentro de {@code {{ }}} (ex: operadores
 * logicos, ternarios) NAO sao suportadas nesta fase - o dev deve expor um
 * metodo de template para logica composta. Isso e uma limitacao conhecida
 * e documentada, nao um bug: escrever um parser de expressoes completo foi
 * uma decisao explicita de ficar para uma fase futura.
 */
final class TemplateTransformer {

    // {{ campo }}, {{ campo? }}, {{ campo! }} ou {{ metodo() }} - identificador
    // simples ou acesso a um nivel (campo.sub), sem operadores, com sufixo
    // opcional de null-safety (? = seguro, ! = assert non-null)
    private static final Pattern INTERPOLATION =
            Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*(?:\\(\\))?)\\s*([?!]?)\\s*\\}\\}");

    // (click)="incrementar()"  - nome do evento + metodo de acao sem argumentos
    private static final Pattern EVENT_BINDING =
            Pattern.compile("\\(([a-zA-Z]+)\\)=\"([a-zA-Z_][a-zA-Z0-9_]*)\\(\\)\"");

    // primeira tag de abertura do template, para injetar o atributo de escopo
    private static final Pattern FIRST_OPEN_TAG = Pattern.compile("^\\s*<([a-zA-Z][a-zA-Z0-9-]*)");

    // {{ 'chave' | translate }} - i18n com verificacao estatica da chave
    // contra messages.properties (ver JtaAnnotationProcessor#messageKeys)
    private static final Pattern TRANSLATE =
            Pattern.compile("\\{\\{\\s*'([^']+)'\\s*\\|\\s*translate\\s*\\}\\}");

    record Result(String generatedJte, List<String> boundActions, List<ValidationError> errors, List<String> referencedFields) {
        boolean hasErrors() {
            return !errors.isEmpty();
        }
    }

    record ValidationError(String kind, String reference, String message) {
    }

    /**
     * @param rawTemplate   template .jta original
     * @param selector      seletor ja resolvido do componente (para o atributo de escopo e o endpoint de acao)
     * @param knownFields   nomes de campos publicos do componente (referenciaveis em {{ }})
     * @param knownMethods  nomes de metodos de template (publicos, sem args, com retorno) referenciaveis em {{ metodo() }}
     * @param knownActions  nomes de metodos de acao (publicos, void, sem args) referenciaveis em (evento)="acao()"
     * @param nullableFields subconjunto de knownFields anotado com algo chamado
     *                       "Nullable" (qualquer pacote - JSpecify, javax, jetbrains -
     *                       comparado pelo nome simples para nao forcar uma dependencia
     *                       especifica). Referenciar um desses campos com {{ campo }}
     *                       puro (sem sufixo) e erro de compilacao - precisa ser
     *                       {{ campo? }} (seguro) ou {{ campo! }} (assert non-null).
     * @param messageKeys   chaves conhecidas em {@code messages.properties} - toda
     *                      chave usada em {{ 'chave' | translate }} precisa existir
     *                      aqui, ou o build falha (i18n com verificacao estatica).
     */
    static Result transform(String rawTemplate, String selector,
                             Set<String> knownFields, Set<String> knownMethods, Set<String> knownActions,
                             Set<String> nullableFields, Set<String> messageKeys) {
        List<ValidationError> errors = new ArrayList<>();
        List<String> boundActions = new ArrayList<>();
        java.util.Set<String> referencedFields = new java.util.LinkedHashSet<>();

        String withScope = injectScopeAttribute(rawTemplate, selector);
        String withEvents = transformEventBindings(withScope, selector, knownActions, boundActions, errors);
        String withTranslations = transformTranslations(withEvents, messageKeys, errors);
        String jte = transformInterpolations(withTranslations, knownFields, knownMethods, nullableFields, errors, referencedFields);

        validateNoStrayAtSigns(jte, errors);

        return new Result(jte, boundActions, errors, List.copyOf(referencedFields));
    }

    // diretivas JTE reconhecidas - qualquer outro '@' no template e texto
    // comum que vai quebrar a compilacao do JTE com um erro interno confuso
    // (ex: "Missing @endfor" apontando pra um arquivo gerado que o dev nunca
    // escreveu). Ver JtaAnnotationProcessor/TROUBLESHOOTING.md.
    private static final Pattern KNOWN_JTE_DIRECTIVE = Pattern.compile(
            "@(if\\(|elseif\\(|else\\b|endif\\b|for\\(|endfor\\b|raw\\b|endraw\\b|import\\s|param\\s|template\\.|tag\\.|layout\\.)");

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

    private static String injectScopeAttribute(String template, String selector) {
        Matcher m = FIRST_OPEN_TAG.matcher(template);
        if (!m.find()) {
            // sem tag HTML na raiz (ex: fragmento so com {{ }}); nada a escopar
            return template;
        }
        int insertAt = m.end();
        return template.substring(0, insertAt)
                + " data-jta-component=\"" + selector + "\""
                + template.substring(insertAt);
    }

    private static String transformEventBindings(String template, String selector, Set<String> knownActions,
                                                   List<String> boundActionsOut, List<ValidationError> errors) {
        Matcher m = EVENT_BINDING.matcher(template);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String event = m.group(1);
            String action = m.group(2);

            if (!knownActions.contains(action)) {
                errors.add(new ValidationError("action", action,
                        "acao '" + action + "()' referenciada em (" + event + ") nao existe como metodo publico void "
                                + "no componente" + DidYouMean.suggest(action, knownActions)));
            } else if (!boundActionsOut.contains(action)) {
                boundActionsOut.add(action);
            }

            String replacement = "hx-post=\"/__jta/action/" + selector + "?action=" + action + "\" "
                    + "hx-trigger=\"" + event + "\" "
                    + "hx-target=\"closest [data-jta-component]\" "
                    + "hx-swap=\"outerHTML\" "
                    + "hx-include=\"closest [data-jta-component]\"";
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
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
}
