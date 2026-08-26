package dev.jta.tck;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * Agrega os relatorios {@code target/jta-tck-report.properties} de cada
 * modulo adaptador (escritos por {@link AbstractJtaTck} apos rodar o TCK
 * contra aquele adaptador) numa matriz Markdown feature x adaptador.
 *
 * <p>Uso: {@code mvn -q verify} (roda o TCK em cada modulo adaptador, que
 * escreve seu proprio relatorio) e depois
 * {@code java -cp jta-tck/target/classes dev.jta.tck.CompatibilityMatrixGenerator <raiz-do-repo> [--check]}.
 * Sem {@code --check}, imprime a matriz gerada em stdout. Com
 * {@code --check}, compara a matriz gerada contra a secao delimitada por
 * {@code MATRIX_BEGIN}/{@code MATRIX_END} em {@code README.md} e sai com
 * codigo 1 se divergir - e o gate que {@code scripts/check-compat-matrix.sh}
 * chama (ver nota nesse script sobre o que falta para isto rodar
 * automaticamente no CI).
 */
public final class CompatibilityMatrixGenerator {

    public static final String MATRIX_BEGIN = "<!-- JTA-TCK-MATRIX-BEGIN (gerado por CompatibilityMatrixGenerator - nao editar a mao) -->";
    public static final String MATRIX_END = "<!-- JTA-TCK-MATRIX-END -->";

    private CompatibilityMatrixGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path repoRoot = Path.of(args.length > 0 ? args[0] : ".");
        boolean check = args.length > 1 && "--check".equals(args[1]);

        List<Properties> reports = findReports(repoRoot);
        if (reports.isEmpty()) {
            System.err.println("Nenhum " + TckReportWriter.REPORT_FILE_NAME + " encontrado sob " + repoRoot.toAbsolutePath()
                    + " - rode 'mvn test' (ou 'verify') nos modulos adaptadores antes de gerar a matriz.");
            System.exit(2);
        }

        String matrix = renderMatrix(reports);

        if (!check) {
            System.out.println(matrix);
            return;
        }

        Path readme = repoRoot.resolve("README.md");
        String documented = extractDocumentedMatrix(readme);
        if (documented == null) {
            System.err.println("README.md nao tem a secao " + MATRIX_BEGIN + " ... " + MATRIX_END
                    + " - adicione-a (rode sem --check e cole a saida) antes de habilitar o gate de CI.");
            System.exit(2);
        }

        if (!documented.strip().equals(matrix.strip())) {
            System.err.println("A matriz de compatibilidade gerada pelo TCK diverge de README.md.");
            System.err.println("Rode sem --check, cole a saida entre " + MATRIX_BEGIN + " e " + MATRIX_END + " em README.md, e commite.");
            System.err.println();
            System.err.println("--- gerada ---");
            System.err.println(matrix);
            System.exit(1);
        }

        System.out.println("Matriz de compatibilidade OK - README.md esta em dia com o TCK.");
    }

    private static List<Properties> findReports(Path repoRoot) throws IOException {
        try (Stream<Path> walk = Files.walk(repoRoot)) {
            return walk
                    .filter(p -> p.getFileName().toString().equals(TckReportWriter.REPORT_FILE_NAME))
                    .filter(p -> "target".equals(p.getParent().getFileName().toString()))
                    .sorted()
                    .map(TckReportWriter::read)
                    .toList();
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }

    /** Feature (ordem do enum) -> adaptador -> {@link TckResult}. */
    static Map<JtaFeature, Map<String, TckResult>> collect(List<Properties> reports) {
        Map<JtaFeature, Map<String, TckResult>> byFeature = new LinkedHashMap<>();
        for (JtaFeature feature : JtaFeature.values()) {
            byFeature.put(feature, new LinkedHashMap<>());
        }

        List<Properties> sorted = reports.stream()
                .sorted(Comparator.comparing(TckReportWriter::adapterNameOf))
                .toList();

        for (Properties props : sorted) {
            String adapter = TckReportWriter.adapterNameOf(props);
            for (JtaFeature feature : JtaFeature.values()) {
                String raw = props.getProperty(feature.name());
                if (raw == null) {
                    continue; // modulo nao rodou esse teste (nao devia acontecer - todo adaptador roda a mesma suite)
                }
                int sep = raw.indexOf('|');
                TckStatus status = TckStatus.valueOf(sep < 0 ? raw : raw.substring(0, sep));
                String detail = sep < 0 || sep == raw.length() - 1 ? null : raw.substring(sep + 1);
                byFeature.get(feature).put(adapter, new TckResult(status, detail));
            }
        }
        return byFeature;
    }

    static String renderMatrix(List<Properties> reports) {
        Map<JtaFeature, Map<String, TckResult>> byFeature = collect(reports);
        List<String> adapters = reports.stream()
                .map(TckReportWriter::adapterNameOf)
                .distinct()
                .sorted()
                .toList();

        StringBuilder sb = new StringBuilder();
        sb.append(MATRIX_BEGIN).append('\n');
        sb.append("| Feature |");
        for (String adapter : adapters) {
            sb.append(' ').append(adapter).append(" |");
        }
        sb.append('\n');
        sb.append("|---|");
        adapters.forEach(a -> sb.append("---|"));
        sb.append('\n');

        for (JtaFeature feature : JtaFeature.values()) {
            sb.append("| ").append(feature.description()).append(" |");
            Map<String, TckResult> byAdapter = byFeature.get(feature);
            for (String adapter : adapters) {
                TckResult result = byAdapter.get(adapter);
                sb.append(' ').append(cell(result)).append(" |");
            }
            sb.append('\n');
        }
        sb.append(MATRIX_END);
        return sb.toString();
    }

    private static String cell(TckResult result) {
        if (result == null) {
            return "?";
        }
        return switch (result.status()) {
            case SUPORTADO -> "✅";
            case IGNORADO -> "⛔";
            case FALHOU -> "❌";
        };
    }

    private static String extractDocumentedMatrix(Path readme) throws IOException {
        if (!Files.exists(readme)) {
            return null;
        }
        String content = Files.readString(readme);
        int begin = content.indexOf(MATRIX_BEGIN);
        int end = content.indexOf(MATRIX_END);
        if (begin < 0 || end < 0 || end < begin) {
            return null;
        }
        return content.substring(begin, end + MATRIX_END.length());
    }
}
