package dev.jta.processor;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compila (via a {@code JavaCompiler} API do proprio JDK, sem depender de
 * nenhuma lib de "compile-testing" - mantendo jta-processor sem
 * dependencias de teste alem de JUnit) uma classe {@code @AComponent}
 * minima contra {@link JtaAnnotationProcessor} de verdade, para verificar
 * em compile-time real a checagem de tipo de campo bindavel adicionada na
 * fase de correcao de dados (ver
 * {@code ConverterRegistry#SUPPORTED_SIMPLE_TYPE_NAMES}, referenciada por
 * {@link JtaAnnotationProcessor#isSupportedFieldType} - mesma fonte de
 * verdade usada em runtime pelo {@code ConverterRegistry}).
 */
class JtaAnnotationProcessorFieldTypeTest {

    private Path sourceDir;
    private Path classOutputDir;
    private Path generatedSourceDir;

    @BeforeEach
    void setUp() throws IOException {
        sourceDir = Files.createTempDirectory("jta-processor-test-src");
        classOutputDir = Files.createTempDirectory("jta-processor-test-classes");
        generatedSourceDir = Files.createTempDirectory("jta-processor-test-generated");
    }

    @AfterEach
    void tearDown() throws IOException {
        deleteRecursively(sourceDir);
        deleteRecursively(classOutputDir);
        deleteRecursively(generatedSourceDir);
    }

    @Test
    void rejectsUnsupportedBindableFieldTypeAtCompileTime() throws IOException {
        String source = """
                package jtatest;

                import dev.jta.core.AComponent;
                import dev.jta.core.Bindable;

                @AComponent(template = "<div></div>")
                public class BadComponent {
                    @Bindable
                    public Object misc;
                }
                """;

        CompileResult result = compile("jtatest.BadComponent", source);

        assertFalse(result.success(), "compilacao deveria falhar por causa do tipo de campo nao suportado");
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                        d.getKind() == Diagnostic.Kind.ERROR
                                && d.getMessage(Locale.ROOT).contains("misc")
                                && d.getMessage(Locale.ROOT).contains("tipo")),
                "esperava um ERROR mencionando o campo 'misc' e seu tipo, achou: " + result.diagnostics());
    }

    @Test
    void acceptsAllTypesSupportedByConverterRegistry() throws IOException {
        String source = """
                package jtatest;

                import dev.jta.core.AComponent;
                import dev.jta.core.Bindable;

                import java.math.BigDecimal;
                import java.time.LocalDate;
                import java.time.LocalDateTime;
                import java.util.List;
                import java.util.Optional;
                import java.util.UUID;

                @AComponent(template = "<div></div>")
                public class GoodComponent {
                    public enum Status { ATIVO, INATIVO }

                    @Bindable public String nome;
                    @Bindable public int idade;
                    @Bindable public Integer idadeWrapper;
                    @Bindable public long total;
                    @Bindable public double preco;
                    @Bindable public boolean ativo;
                    @Bindable public BigDecimal valor;
                    @Bindable public UUID id;
                    @Bindable public LocalDate nascimento;
                    @Bindable public LocalDateTime criadoEm;
                    @Bindable public Status status;
                    @Bindable public List<Integer> tags;
                    @Bindable public Integer[] tagsArray;
                    @Bindable public Optional<String> apelido;
                }
                """;

        CompileResult result = compile("jtatest.GoodComponent", source);

        assertTrue(result.success(), "compilacao nao deveria falhar - todos os tipos sao suportados: " + result.diagnostics());
    }

    private record CompileResult(boolean success, List<Diagnostic<? extends JavaFileObject>> diagnostics) {
    }

    private CompileResult compile(String fqn, String source) throws IOException {
        String packagePath = fqn.substring(0, fqn.lastIndexOf('.')).replace('.', '/');
        String simpleName = fqn.substring(fqn.lastIndexOf('.') + 1);
        Path packageDir = sourceDir.resolve(packagePath);
        Files.createDirectories(packageDir);
        Path sourceFile = packageDir.resolve(simpleName + ".java");
        Files.writeString(sourceFile, source);

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, StandardCharsets.UTF_8)) {
            fileManager.setLocation(StandardLocation.CLASS_OUTPUT, List.of(classOutputDir.toFile()));
            fileManager.setLocation(StandardLocation.SOURCE_OUTPUT, List.of(generatedSourceDir.toFile()));

            Iterable<? extends JavaFileObject> units = fileManager.getJavaFileObjectsFromPaths(List.of(sourceFile));

            List<String> options = List.of(
                    "-classpath", System.getProperty("java.class.path"),
                    "-processor", "dev.jta.processor.JtaAnnotationProcessor");

            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, units);
            boolean success = task.call();
            return new CompileResult(success, diagnostics.getDiagnostics());
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort cleanup de diretorio temporario
                }
            });
        }
    }
}
