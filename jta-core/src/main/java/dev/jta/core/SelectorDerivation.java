package dev.jta.core;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Deriva um seletor canonico e globalmente unico a partir do nome
 * totalmente qualificado (FQN) de uma classe de componente.
 *
 * <p>A garantia de unicidade vem de graca: dois componentes nunca tem o
 * mesmo FQN (o proprio compilador Java ja impede isso), entao o seletor
 * derivado nunca colide. Isso e o comportamento padrao quando
 * {@link AComponent#selector()} e deixado em branco.
 *
 * <p>Exemplo: {@code com.acme.ui.components.UserCard} vira
 * {@code acme-ui-components-user-card}.
 *
 * <p>Esta classe e usada tanto pelo annotation processor (compile-time,
 * para gerar o atributo {@code data-jta-component} e o endpoint de acao)
 * quanto em runtime (para resolver qual classe instanciar a partir do
 * seletor recebido numa requisicao HTMX) - por isso vive em jta-core, sem
 * dependencia de ferramentas de build.
 */
public final class SelectorDerivation {

    private static final Set<String> DEFAULT_STRIPPED_PREFIXES = Set.of("com", "org", "io", "net");
    private static final Pattern CAMEL_BOUNDARY = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

    private SelectorDerivation() {
    }

    /**
     * Deriva o seletor canonico usando as opcoes padrao (remove prefixo de
     * dominio invertido comum, separador "-").
     */
    public static String derive(String fullyQualifiedClassName) {
        return derive(fullyQualifiedClassName, true, "-");
    }

    /**
     * Deriva o seletor canonico com opcoes explicitas, espelhando
     * {@code [selector]} em {@code jta.config.toml}.
     *
     * @param fullyQualifiedClassName FQN completo, ex: {@code com.acme.ui.Button}
     * @param stripDomainPrefix       remove o primeiro segmento se for um TLD comum (com/org/io/net)
     * @param separator               separador entre segmentos do pacote e da classe (padrao "-")
     */
    public static String derive(String fullyQualifiedClassName, boolean stripDomainPrefix, String separator) {
        if (fullyQualifiedClassName == null || fullyQualifiedClassName.isBlank()) {
            throw new IllegalArgumentException("fullyQualifiedClassName nao pode ser vazio");
        }

        List<String> segments = new java.util.ArrayList<>(List.of(fullyQualifiedClassName.split("\\.")));

        if (stripDomainPrefix && segments.size() > 1 && DEFAULT_STRIPPED_PREFIXES.contains(segments.get(0))) {
            segments.remove(0);
        }

        StringBuilder result = new StringBuilder();
        for (int i = 0; i < segments.size(); i++) {
            if (i > 0) {
                result.append(separator);
            }
            result.append(toKebabCase(segments.get(i)));
        }

        String selector = result.toString();

        // Remove qualquer caractere que nao seja letra/digito/separador
        // escolhido - preserva o separador em vez de assumir "-" sempre.
        String separatorClass = "\\Q" + separator + "\\E";
        selector = selector.replaceAll("[^a-z0-9" + separatorClass + "]", "");

        // Convencao (nao exigencia de spec, ja que os tags de componente
        // sao resolvidos em compile-time, nao registrados como Custom
        // Elements no browser): com o separador padrao "-", garantir ao
        // menos um hifen para manter a legibilidade de nomes de classe
        // com um unico segmento de pacote.
        if ("-".equals(separator) && !selector.contains("-")) {
            selector = "c-" + selector;
        }
        return selector;
    }

    private static String toKebabCase(String pascalOrCamel) {
        String withBoundaries = CAMEL_BOUNDARY.matcher(pascalOrCamel).replaceAll("-");
        return withBoundaries.toLowerCase(java.util.Locale.ROOT);
    }
}
