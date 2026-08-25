package dev.jta.tck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

/**
 * Persiste o resultado de uma execucao do TCK para um adaptador como um
 * arquivo {@code .properties} simples em {@code target/} (sem depender de
 * nenhuma lib de serializacao - mesmo espirito zero-dependencias deste
 * modulo). {@link CompatibilityMatrixGenerator} depois varre o repositorio
 * procurando por esses arquivos (um por modulo adaptador que rodou o TCK)
 * para montar a matriz agregada.
 */
public final class TckReportWriter {

    /** Nome do arquivo de relatorio dentro de {@code target/} - buscado pelo generator via glob no repositorio inteiro. */
    public static final String REPORT_FILE_NAME = "jta-tck-report.properties";

    private static final String ADAPTER_NAME_KEY = "__adapter__";

    private TckReportWriter() {
    }

    /** {@code targetDir} e o {@code target/} do modulo do adaptador em execucao (normalmente {@code System.getProperty("user.dir") + "/target"}). */
    public static void write(String adapterName, Map<JtaFeature, TckResult> results, Path targetDir) {
        Properties props = new Properties();
        props.setProperty(ADAPTER_NAME_KEY, adapterName);
        for (Map.Entry<JtaFeature, TckResult> entry : results.entrySet()) {
            TckResult result = entry.getValue();
            String detail = result.detail() == null ? "" : result.detail();
            props.setProperty(entry.getKey().name(), result.status().name() + "|" + detail);
        }

        try {
            Files.createDirectories(targetDir);
            try (var writer = Files.newBufferedWriter(targetDir.resolve(REPORT_FILE_NAME))) {
                props.store(writer, "Relatorio TCK do JTA - gerado automaticamente, nao editar a mao");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao escrever o relatorio TCK em " + targetDir, e);
        }
    }

    public static Properties read(Path reportFile) {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(reportFile)) {
            props.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao ler o relatorio TCK em " + reportFile, e);
        }
        return props;
    }

    public static String adapterNameOf(Properties props) {
        return props.getProperty(ADAPTER_NAME_KEY, "?");
    }
}
