package dev.jta.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova, via um harness de {@code javac} de verdade (mesmo espirito de
 * {@link NestingCycleCompileTest}), a PRIMEIRA camada de defesa contra o
 * mass-assignment que {@code dev.jta.core.ReservedFieldNames} documenta:
 * um campo publico explicitamente {@code @Bindable} com um nome reservado
 * (ex: {@code flashSuccess}) tem que falhar o BUILD, nao so ser ignorado
 * silenciosamente em runtime (essa segunda camada ja e coberta por
 * {@code ComponentInvokerReservedFieldsTest} em jta-runtime).
 */
class ReservedFieldNameCompileTest {

    @Test
    void campoBindableComNomeReservadoFalhaOBuild(@TempDir Path tempDir) throws IOException {
        Path srcDir = tempDir.resolve("src/dev/jta/reservedtest");
        Files.createDirectories(srcDir);
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);

        writeSource(srcDir.resolve("FlashForjada.java"), """
                package dev.jta.reservedtest;
                import dev.jta.core.AComponent;
                import dev.jta.core.Bindable;

                @AComponent(selector = "reserved-flash", template = "<div>{{ flashSuccess }}</div>")
                public class FlashForjada {
                    @Bindable
                    public String flashSuccess;
                }
                """);

        CompileResult result = compile(srcDir, outDir);

        assertFalse(result.success(), "esperava que a compilacao FALHASSE - 'flashSuccess' e um nome reservado, "
                + "nao pode ser @Bindable. Saida:\n" + result.diagnostics());
        assertTrue(result.diagnostics().contains("flashSuccess") && result.diagnostics().contains("reservado"),
                "esperava uma mensagem de erro mencionando o nome reservado, saida real:\n" + result.diagnostics());
    }

    @Test
    void campoInterpoladoComNomeReservadoCompilaMasNaoEBindavel(@TempDir Path tempDir) throws IOException {
        // sem @Bindable: so interpolado no template (ver
        // JtaAnnotationProcessor#processComponent - removido em silencio
        // de bindableFields, nao e erro so por ser referenciado).
        Path srcDir = tempDir.resolve("src/dev/jta/reservedtest2");
        Files.createDirectories(srcDir);
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);

        writeSource(srcDir.resolve("FlashOk.java"), """
                package dev.jta.reservedtest2;
                import dev.jta.core.AComponent;

                @AComponent(selector = "reserved-flash-ok", template = "<div>{{ flashSuccess }}</div>")
                public class FlashOk {
                    public String flashSuccess;
                }
                """);

        CompileResult result = compile(srcDir, outDir);

        assertTrue(result.success(), "campo reservado so interpolado (sem @Bindable) deve compilar - "
                + "a protecao e ele nao entrar em bindableFields, nao um erro de build. Saida:\n" + result.diagnostics());
    }

    private record CompileResult(boolean success, String diagnostics) {
    }

    private CompileResult compile(Path srcDir, Path outDir) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

        try (StandardJavaFileManager fm = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> sources = fm.getJavaFileObjectsFromPaths(
                    Files.walk(srcDir).filter(p -> p.toString().endsWith(".java")).toList());

            String classpath = System.getProperty("java.class.path");
            List<String> options = List.of(
                    "-classpath", classpath,
                    "-processorpath", classpath,
                    "-d", outDir.toString(),
                    "-s", outDir.toString(),
                    "-proc:only"
            );

            boolean success = compiler.getTask(null, fm, diagnostics, options, null, sources).call();
            String output = diagnostics.getDiagnostics().stream()
                    .map(d -> d.getKind() + ": " + d.getMessage(null))
                    .collect(Collectors.joining("\n"));
            boolean hasErrors = diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
            return new CompileResult(success && !hasErrors, output);
        }
    }

    private void writeSource(Path path, String content) throws IOException {
        try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }
}
