package dev.jta.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Carrega em runtime os {@link ComponentMetadata} gerados por todos os
 * jars presentes no classpath (o proprio projeto + qualquer dependencia
 * que traga componentes JTA).
 *
 * <p>Cada jar contribui com um {@code META-INF/jta/components.json}
 * proprio; esta classe agrega todos eles. A verificacao de colisao de
 * selector explicito ja aconteceu em compile-time (dentro de cada modulo
 * compilado com o processor), mas quando dois jars *independentes* sao
 * combinados em runtime sem terem sido compilados juntos, a colisao so
 * pode ser detectada aqui - por isso o load falha ruidosamente em vez de
 * silenciosamente sobrescrever uma entrada.
 */
public final class ComponentRegistry {

    private static final String METADATA_RESOURCE = "META-INF/jta/components.json";

    private final Map<String, ComponentMetadata> bySelector;
    private final Map<String, ComponentMetadata> byFqn;

    private ComponentRegistry(Map<String, ComponentMetadata> bySelector, Map<String, ComponentMetadata> byFqn) {
        this.bySelector = Collections.unmodifiableMap(bySelector);
        this.byFqn = Collections.unmodifiableMap(byFqn);
    }

    public static ComponentRegistry loadFromClasspath(ClassLoader classLoader) {
        Map<String, ComponentMetadata> bySelector = new HashMap<>();
        Map<String, ComponentMetadata> byFqn = new HashMap<>();

        try {
            Enumeration<URL> resources = classLoader.getResources(METADATA_RESOURCE);
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                try (InputStream in = resource.openStream()) {
                    String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    List<ComponentMetadata> declared = JsonIo.readList(json);
                    for (ComponentMetadata metadata : declared) {
                        ComponentMetadata previous = bySelector.putIfAbsent(metadata.selector(), metadata);
                        if (previous != null && !previous.fqn().equals(metadata.fqn())) {
                            throw new IllegalStateException(
                                    "Selector JTA duplicado entre jars independentes: '" + metadata.selector()
                                            + "' e reivindicado por " + previous.fqn() + " e " + metadata.fqn()
                                            + ". Use @Use(type=..., as=\"...\") no consumidor para resolver o alias.");
                        }
                        byFqn.put(metadata.fqn(), metadata);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao carregar metadados de componentes JTA do classpath", e);
        }

        return new ComponentRegistry(bySelector, byFqn);
    }

    public ComponentMetadata bySelector(String selector) {
        ComponentMetadata metadata = bySelector.get(selector);
        if (metadata == null) {
            throw new IllegalArgumentException("Nenhum componente JTA registrado com o selector '" + selector + "'");
        }
        return metadata;
    }

    public ComponentMetadata byFqn(String fqn) {
        ComponentMetadata metadata = byFqn.get(fqn);
        if (metadata == null) {
            throw new IllegalArgumentException("Nenhum componente JTA registrado para a classe '" + fqn + "'");
        }
        return metadata;
    }

    public List<ComponentMetadata> pages() {
        return bySelector.values().stream().filter(ComponentMetadata::isPage).toList();
    }

    public List<ComponentMetadata> all() {
        return List.copyOf(bySelector.values());
    }
}
