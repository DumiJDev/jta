package dev.jta.standalone;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Casamento de padrao de rota ("/produto/{id}" contra "/produto/42"),
 * unico bloco de routing genuino desta entrega - todos os outros
 * adaptadores (Spring MVC, Javalin, Quarkus/Vert.x) ja trazem o proprio
 * router e so extraem as path variables do que ja casou. O standalone nao
 * tem framework nenhum por baixo do {@code com.sun.net.httpserver.HttpServer},
 * entao precisa reimplementar esse casamento aqui.
 */
final class RoutePattern {

    private static final Pattern VARIABLE = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)\\}");

    private final String routePath;
    private final Pattern compiled;
    private final List<String> variableNames;

    private RoutePattern(String routePath, Pattern compiled, List<String> variableNames) {
        this.routePath = routePath;
        this.compiled = compiled;
        this.variableNames = variableNames;
    }

    static RoutePattern compile(String routePath) {
        StringBuilder regex = new StringBuilder();
        List<String> names = new ArrayList<>();
        Matcher segments = VARIABLE.matcher(routePath);
        int lastEnd = 0;
        while (segments.find()) {
            regex.append(Pattern.quote(routePath.substring(lastEnd, segments.start())));
            names.add(segments.group(1));
            regex.append("([^/]+)");
            lastEnd = segments.end();
        }
        regex.append(Pattern.quote(routePath.substring(lastEnd)));
        return new RoutePattern(routePath, Pattern.compile(regex.toString()), names);
    }

    /** Menos variaveis = padrao mais especifico - usado para desempatar quando varios padroes casariam o mesmo path. */
    int variableCount() {
        return variableNames.size();
    }

    String routePath() {
        return routePath;
    }

    /** Devolve as path variables extraidas, ou {@code null} se o path nao casar com este padrao. */
    Map<String, String> match(String path) {
        Matcher matcher = compiled.matcher(path);
        if (!matcher.matches()) {
            return null;
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < variableNames.size(); i++) {
            variables.put(variableNames.get(i), matcher.group(i + 1));
        }
        return variables;
    }
}
