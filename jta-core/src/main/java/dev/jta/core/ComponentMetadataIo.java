package dev.jta.core;

import java.util.List;

/**
 * Ponto de entrada publico para serializar/desserializar
 * {@link ComponentMetadata}, usado pelo {@code JtaAnnotationProcessor}
 * (modulo {@code jta-processor}) para escrever {@code components.json}
 * exatamente no formato que {@link ComponentRegistry} sabe ler em
 * runtime. Mantem o formato definido num unico lugar (jta-core) mesmo o
 * escritor e o leitor vivendo em fases diferentes do ciclo de vida do
 * build (compile-time vs runtime).
 */
public final class ComponentMetadataIo {

    private ComponentMetadataIo() {
    }

    public static String toJson(List<ComponentMetadata> items) {
        return JsonIo.writeList(items);
    }

    public static List<ComponentMetadata> fromJson(String json) {
        return JsonIo.readList(json);
    }
}
