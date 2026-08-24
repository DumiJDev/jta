package dev.jta.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Leitor minimo de {@code jta.config.toml}, dependency-free (mesma
 * filosofia de {@link JsonIo}: o schema de configuracao do JTA e
 * deliberadamente simples, entao nao justifica puxar uma lib TOML
 * completa como dependencia obrigatoria de todo projeto).
 *
 * <p><b>Suporta:</b> secoes {@code [secao]} e pares {@code chave = valor}
 * com valores string ({@code "..."}), boolean ({@code true}/{@code false})
 * ou inteiro. <b>Nao suporta:</b> arrays, tabelas aninhadas, datas,
 * multi-line strings - se o arquivo do dev precisar disso, e sinal de que
 * o schema de config do JTA cresceu alem do que esta classe cobre; nesse
 * caso trocar por uma lib TOML real (ex: tomlj) e revisitar essa decisao.
 *
 * <p>Exemplo suportado:
 * <pre>{@code
 * [selector]
 * strip_domain_prefix = true
 * separator = "-"
 *
 * [htmx]
 * cdn_url = "https://unpkg.com/htmx.org@2.0.4"
 * }</pre>
 */
public final class JtaConfig {

    private final Map<String, Map<String, String>> sections;

    private JtaConfig(Map<String, Map<String, String>> sections) {
        this.sections = sections;
    }

    /** Config vazia - usada quando {@code jta.config.toml} nao existe no projeto (todas as leituras caem no default do chamador). */
    public static JtaConfig empty() {
        return new JtaConfig(Map.of());
    }

    public static JtaConfig parse(String tomlContent) {
        Map<String, Map<String, String>> sections = new HashMap<>();
        String currentSection = "";
        sections.put(currentSection, new HashMap<>());

        for (String rawLine : tomlContent.split("\n")) {
            String line = stripComment(rawLine).trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1).trim();
                sections.putIfAbsent(currentSection, new HashMap<>());
                continue;
            }
            int eq = line.indexOf('=');
            if (eq < 0) {
                throw new IllegalArgumentException("Linha invalida em jta.config.toml (esperado 'chave = valor'): " + rawLine);
            }
            String key = line.substring(0, eq).trim();
            String value = line.substring(eq + 1).trim();
            sections.get(currentSection).put(key, value);
        }
        return new JtaConfig(sections);
    }

    /** Le de um arquivo no filesystem; retorna {@link #empty()} se o arquivo nao existir. */
    public static JtaConfig loadFromFile(Path path) {
        if (!Files.exists(path)) {
            return empty();
        }
        try {
            return parse(Files.readString(path));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler " + path, e);
        }
    }

    /**
     * Le {@code jta.config.toml} do classpath (ex: {@code src/main/resources/jta.config.toml}
     * num projeto Maven/Gradle padrao) - retorna {@link #empty()} se o
     * arquivo nao existir, para que todo consumidor caia graciosamente
     * nos defaults sem exigir o arquivo.
     */
    public static JtaConfig loadFromClasspath(ClassLoader classLoader) {
        try (var in = classLoader.getResourceAsStream("jta.config.toml")) {
            if (in == null) {
                return empty();
            }
            return parse(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler jta.config.toml do classpath", e);
        }
    }

    public String getString(String section, String key, String defaultValue) {
        String raw = raw(section, key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw.length() >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
            return raw.substring(1, raw.length() - 1);
        }
        throw new IllegalArgumentException("Valor de [" + section + "] " + key + " deveria ser uma string entre aspas: " + raw);
    }

    public boolean getBoolean(String section, String key, boolean defaultValue) {
        String raw = raw(section, key);
        if (raw == null) {
            return defaultValue;
        }
        if (raw.equals("true") || raw.equals("false")) {
            return Boolean.parseBoolean(raw);
        }
        throw new IllegalArgumentException("Valor de [" + section + "] " + key + " deveria ser true/false: " + raw);
    }

    public int getInt(String section, String key, int defaultValue) {
        String raw = raw(section, key);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Valor de [" + section + "] " + key + " deveria ser um inteiro: " + raw, e);
        }
    }

    private String raw(String section, String key) {
        Map<String, String> s = sections.get(section);
        return s == null ? null : s.get(key);
    }

    private static String stripComment(String line) {
        int hashIndex = findUnquotedHash(line);
        return hashIndex < 0 ? line : line.substring(0, hashIndex);
    }

    private static int findUnquotedHash(String line) {
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == '#' && !inQuotes) {
                return i;
            }
        }
        return -1;
    }
}
