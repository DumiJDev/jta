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
 * {@code scripts/smoke-test.sh}, mas rodando dentro do proprio JVM de
 * teste via {@link JavaCompiler}, sem shell out), que um ciclo de
 * aninhamento de componentes (A aninha B aninha A) falha o build com
 * {@link JtaAnnotationProcessor} no processor path.
 *
 * <p>Usa o classpath ATUAL da JVM de teste (que ja inclui jta-core e as
 * classes compiladas de jta-processor, colocadas la pelo proprio Maven
 * Surefire) tanto como classpath quanto como processor path da
 * sub-compilacao - evita reimplementar a resolucao de artefatos Maven so
 * para este teste.
 */
class NestingCycleCompileTest {

    @Test
    void componentesQueSeAninhamEmCicloFalhamOBuild(@TempDir Path tempDir) throws IOException {
        Path srcDir = tempDir.resolve("src/dev/jta/cycletest");
        Files.createDirectories(srcDir);
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);

        writeSource(srcDir.resolve("A.java"), """
                package dev.jta.cycletest;
                import dev.jta.core.AComponent;

                @AComponent(selector = "cycle-a", template = "<div><cycle-b/></div>")
                public class A {
                }
                """);
        writeSource(srcDir.resolve("B.java"), """
                package dev.jta.cycletest;
                import dev.jta.core.AComponent;

                @AComponent(selector = "cycle-b", template = "<div><cycle-a/></div>")
                public class B {
                }
                """);

        CompileResult result = compile(srcDir, outDir);

        assertFalse(result.success(), "esperava que a compilacao FALHASSE por causa do ciclo de aninhamento, mas passou. Saida:\n" + result.diagnostics());
        assertTrue(result.diagnostics().contains("ciclo de aninhamento"),
                "esperava uma mensagem de erro mencionando 'ciclo de aninhamento', saida real:\n" + result.diagnostics());
    }

    @Test
    void componentesSemCicloCompilamComSucesso(@TempDir Path tempDir) throws IOException {
        Path srcDir = tempDir.resolve("src/dev/jta/nocycletest");
        Files.createDirectories(srcDir);
        Path outDir = tempDir.resolve("out");
        Files.createDirectories(outDir);

        writeSource(srcDir.resolve("Parent.java"), """
                package dev.jta.nocycletest;
                import dev.jta.core.AComponent;

                @AComponent(selector = "nocycle-parent", template = "<div><nocycle-child/></div>")
                public class Parent {
                }
                """);
        writeSource(srcDir.resolve("Child.java"), """
                package dev.jta.nocycletest;
                import dev.jta.core.AComponent;

                @AComponent(selector = "nocycle-child", template = "<div>oi</div>")
                public class Child {
                }
                """);

        CompileResult result = compile(srcDir, outDir);

        assertTrue(result.success(), "esperava compilacao bem-sucedida (sem ciclo), saida real:\n" + result.diagnostics());
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
                    "-proc:only" // so nos interessa rodar o annotation processor, nao gerar bytecode dos .jte
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
