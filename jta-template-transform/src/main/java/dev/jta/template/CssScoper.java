package dev.jta.template;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementa o encapsulamento de CSS por atributo descrito na secao 6 do
 * documento de arquitetura: cada regra top-level do {@code style()} do
 * componente e prefixada com {@code [data-jta-component="<selector>"]},
 * para isolar visualmente sem depender de Shadow DOM (que nao funciona
 * bem com HTMX - ver discussao de design).
 *
 * <p><b>Limitacao conhecida do MVP:</b> transformacao regex-based, nao um
 * parser CSS real. Regras dentro de {@code @media}/{@code @supports} nao
 * sao prefixadas corretamente (o at-rule inteiro e deixado como esta,
 * sem escopo) - documentado, nao silencioso. {@code @keyframes} tambem
 * nao e escopado (nomes de keyframe sao globais por natureza do CSS, nao
 * precisam ser). Um parser CSS real fica para uma fase futura, mesma
 * decisao de escopo aplicada a TemplateTransformer.
 */
public final class CssScoper {

    // "seletor1, seletor2 { declaracoes }" - nao entra em blocos @-rule
    private static final Pattern RULE = Pattern.compile("([^{}@]+)\\{([^{}]*)\\}");

    private CssScoper() {
    }

    public static String scope(String rawCss, String selector) {
        if (rawCss == null || rawCss.isBlank()) {
            return "";
        }
        String attributeSelector = "[data-jta-component=\"" + selector + "\"]";
        Matcher m = RULE.matcher(rawCss);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String selectors = m.group(1).trim();
            String declarations = m.group(2);

            StringBuilder scopedSelectors = new StringBuilder();
            String[] parts = selectors.split(",");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    scopedSelectors.append(", ");
                }
                scopedSelectors.append(attributeSelector).append(" ").append(parts[i].trim());
            }

            m.appendReplacement(out, Matcher.quoteReplacement(scopedSelectors + " {" + declarations + "}"));
        }
        m.appendTail(out);
        return out.toString();
    }
}
