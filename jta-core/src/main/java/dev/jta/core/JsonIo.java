package dev.jta.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Serializacao/deserializacao JSON minima, propria, usada apenas para o
 * schema plano de {@link ComponentMetadata}. Existe para manter jta-core
 * sem dependencias externas (nem Jackson, nem Gson) - o schema e simples
 * o suficiente (objetos planos com string/boolean/lista-de-string) para
 * nao justificar puxar uma lib de JSON completa para dentro de todo
 * projeto que usar o JTA.
 *
 * <p>Nao e um parser JSON de proposito geral: nao lida com numeros,
 * objetos aninhados alem do necessario, nem unicode escaping alem do
 * basico. Se o schema de {@link ComponentMetadata} crescer em
 * complexidade, revisitar essa decisao.
 */
final class JsonIo {

    private JsonIo() {
    }

    /**
     * Publicamente exposto (via {@link ComponentMetadataIo}) para que o
     * annotation processor - que roda em compile-time, num modulo
     * separado, e nao pode depender de jta-core em runtime do mesmo jeito
     * que os componentes dependem - consiga serializar a mesma forma que
     * {@link ComponentRegistry} sabe ler, sem duplicar o formato em dois
     * lugares.
     */
    static String writeList(List<ComponentMetadata> items) {
        StringBuilder sb = new StringBuilder("[\n");
        for (int i = 0; i < items.size(); i++) {
            ComponentMetadata m = items.get(i);
            sb.append("  {\n");
            sb.append("    \"fqn\": ").append(quote(m.fqn())).append(",\n");
            sb.append("    \"selector\": ").append(quote(m.selector())).append(",\n");
            sb.append("    \"explicitSelector\": ").append(m.explicitSelector()).append(",\n");
            sb.append("    \"routePath\": ").append(quoteNullable(m.routePath())).append(",\n");
            sb.append("    \"actions\": ").append(writeStringArray(m.actions())).append(",\n");
            sb.append("    \"generatedJteTemplate\": ").append(quoteNullable(m.generatedJteTemplate())).append(",\n");
            sb.append("    \"scopedCss\": ").append(quoteNullable(m.scopedCss())).append(",\n");
            sb.append("    \"isLayout\": ").append(m.isLayout()).append(",\n");
            sb.append("    \"layoutFqn\": ").append(quoteNullable(m.layoutFqn())).append(",\n");
            sb.append("    \"requiredRoles\": ").append(writeStringArray(m.requiredRoles())).append(",\n");
            sb.append("    \"allowAnonymous\": ").append(m.allowAnonymous()).append(",\n");
            sb.append("    \"ssePath\": ").append(quoteNullable(m.ssePath())).append(",\n");
            sb.append("    \"sseIntervalMillis\": ").append(m.sseIntervalMillis()).append(",\n");
            sb.append("    \"bindableFields\": ").append(writeStringArray(m.bindableFields())).append(",\n");
            sb.append("    \"csrfExempt\": ").append(m.csrfExempt()).append("\n");
            sb.append("  }");
            if (i < items.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("]\n");
        return sb.toString();
    }

    static List<ComponentMetadata> readList(String json) {
        List<ComponentMetadata> result = new ArrayList<>();
        Cursor c = new Cursor(json);
        c.expect('[');
        c.skipWhitespace();
        if (c.peek() == ']') {
            return result;
        }
        while (true) {
            c.skipWhitespace();
            result.add(readObject(c));
            c.skipWhitespace();
            if (c.peek() == ',') {
                c.next();
                continue;
            }
            break;
        }
        c.skipWhitespace();
        c.expect(']');
        return result;
    }

    private static ComponentMetadata readObject(Cursor c) {
        c.expect('{');
        String fqn = null, selector = null, routePath = null, template = null, scopedCss = null, layoutFqn = null, ssePath = null;
        boolean explicitSelector = false;
        boolean isLayout = false;
        boolean allowAnonymous = false;
        long sseIntervalMillis = 0;
        List<String> bindableFields = List.of();
        List<String> actions = List.of();
        List<String> requiredRoles = List.of();
        boolean csrfExempt = false;

        c.skipWhitespace();
        while (c.peek() != '}') {
            c.skipWhitespace();
            String key = c.readString();
            c.skipWhitespace();
            c.expect(':');
            c.skipWhitespace();
            switch (key) {
                case "fqn" -> fqn = c.readString();
                case "selector" -> selector = c.readString();
                case "explicitSelector" -> explicitSelector = c.readBoolean();
                case "routePath" -> routePath = c.readNullableString();
                case "actions" -> actions = c.readStringArray();
                case "generatedJteTemplate" -> template = c.readNullableString();
                case "scopedCss" -> scopedCss = c.readNullableString();
                case "isLayout" -> isLayout = c.readBoolean();
                case "layoutFqn" -> layoutFqn = c.readNullableString();
                case "requiredRoles" -> requiredRoles = c.readStringArray();
                case "allowAnonymous" -> allowAnonymous = c.readBoolean();
                case "ssePath" -> ssePath = c.readNullableString();
                case "sseIntervalMillis" -> sseIntervalMillis = c.readLong();
                case "bindableFields" -> bindableFields = c.readStringArray();
                case "csrfExempt" -> csrfExempt = c.readBoolean();
                default -> c.skipValue();
            }
            c.skipWhitespace();
            if (c.peek() == ',') {
                c.next();
            }
            c.skipWhitespace();
        }
        c.expect('}');
        return new ComponentMetadata(fqn, selector, explicitSelector, routePath, actions, template, scopedCss,
                isLayout, layoutFqn, requiredRoles, allowAnonymous, ssePath, sseIntervalMillis, bindableFields,
                csrfExempt);
    }

    private static String writeStringArray(List<String> values) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            sb.append(quote(values.get(i)));
            if (i < values.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> sb.append(ch);
            }
        }
        return sb.append('"').toString();
    }

    private static String quoteNullable(String value) {
        return value == null ? "null" : quote(value);
    }

    private static final class Cursor {
        private final String src;
        private int pos = 0;

        Cursor(String src) {
            this.src = src;
        }

        char peek() {
            skipWhitespace();
            return src.charAt(pos);
        }

        char next() {
            return src.charAt(pos++);
        }

        void expect(char ch) {
            skipWhitespace();
            if (src.charAt(pos) != ch) {
                throw new IllegalStateException("Esperava '" + ch + "' na posicao " + pos + " mas encontrou '" + src.charAt(pos) + "'");
            }
            pos++;
        }

        void skipWhitespace() {
            while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) {
                pos++;
            }
        }

        String readString() {
            skipWhitespace();
            if (src.charAt(pos) != '"') {
                throw new IllegalStateException("Esperava string na posicao " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (src.charAt(pos) != '"') {
                char ch = src.charAt(pos);
                if (ch == '\\') {
                    pos++;
                    char escaped = src.charAt(pos);
                    switch (escaped) {
                        case 'n' -> sb.append('\n');
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        default -> sb.append(escaped);
                    }
                } else {
                    sb.append(ch);
                }
                pos++;
            }
            pos++;
            return sb.toString();
        }

        String readNullableString() {
            skipWhitespace();
            if (src.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            return readString();
        }

        boolean readBoolean() {
            skipWhitespace();
            if (src.startsWith("true", pos)) {
                pos += 4;
                return true;
            }
            if (src.startsWith("false", pos)) {
                pos += 5;
                return false;
            }
            throw new IllegalStateException("Esperava boolean na posicao " + pos);
        }

        long readLong() {
            skipWhitespace();
            int start = pos;
            if (pos < src.length() && src.charAt(pos) == '-') {
                pos++;
            }
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
                pos++;
            }
            if (pos == start) {
                throw new IllegalStateException("Esperava numero na posicao " + pos);
            }
            return Long.parseLong(src.substring(start, pos));
        }

        List<String> readStringArray() {
            List<String> result = new ArrayList<>();
            expect('[');
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                result.add(readString());
                skipWhitespace();
                if (peek() == ',') {
                    pos++;
                    continue;
                }
                break;
            }
            expect(']');
            return result;
        }

        void skipValue() {
            skipWhitespace();
            char ch = peek();
            if (ch == '"') {
                readString();
            } else if (ch == '[') {
                readStringArray();
            } else if (src.startsWith("null", pos)) {
                pos += 4;
            } else if (src.startsWith("true", pos)) {
                pos += 4;
            } else if (src.startsWith("false", pos)) {
                pos += 5;
            } else if (ch == '-' || Character.isDigit(ch)) {
                readLong();
            } else {
                throw new IllegalStateException("Valor JSON nao suportado na posicao " + pos);
            }
        }
    }
}
