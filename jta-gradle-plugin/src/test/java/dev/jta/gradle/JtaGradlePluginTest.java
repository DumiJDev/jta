package dev.jta.gradle;

import gg.jte.ContentType;
import gg.jte.gradle.JteExtension;
import org.gradle.api.Project;
import org.gradle.api.tasks.compile.JavaCompile;
import org.gradle.process.CommandLineArgumentProvider;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste unitario (via {@link ProjectBuilder}, sem spawnar um build Gradle
 * real - suficiente para verificar que o plugin fia a config certa, sem o
 * custo de um teste funcional completo) - prova que
 * {@link JtaGradlePlugin#apply} delega corretamente para {@code gg.jte.gradle}
 * e propaga {@code -Ajta.resourcesDir} para {@link JavaCompile}.
 */
class JtaGradlePluginTest {

    @Test
    void aplicaJteGradleEConfiguraExtensao() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(JtaGradlePlugin.class);

        assertTrue(project.getPluginManager().hasPlugin("gg.jte.gradle"),
                "esperava que gg.jte.gradle tivesse sido aplicado");

        JteExtension jte = project.getExtensions().getByType(JteExtension.class);
        assertEquals(ContentType.Html, jte.getContentType().get());

        Path expectedSource = new File(project.getLayout().getBuildDirectory().get().getAsFile(),
                "generated/sources/annotations/java/main/jta-templates").toPath();
        assertEquals(expectedSource, jte.getSourceDirectory().get());
    }

    @Test
    void propagaResourcesDirComoOpcaoDoCompilerParaTodoJavaCompile() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(JtaGradlePlugin.class);

        JavaCompile compileJava = (JavaCompile) project.getTasks().getByName("compileJava");
        String expectedArg = "-Ajta.resourcesDir=" + new File(project.getProjectDir(), "src/main/resources").getAbsolutePath();

        assertTrue(resolveArgumentProviders(compileJava).contains(expectedArg),
                "esperava '" + expectedArg + "' em " + resolveArgumentProviders(compileJava));
    }

    /**
     * Prova que a config de {@code -Ajta.resourcesDir} e resolvida
     * preguicosamente (via {@code compilerArgumentProviders}, nao
     * {@code compilerArgs} direto) - um {@code jta { resourcesDir = ... } }
     * no build script do consumidor roda DEPOIS do plugin ser aplicado
     * (plugins {} primeiro, extensoes configuradas depois), entao capturar
     * o valor eager em {@link JtaGradlePlugin#apply} sempre pegaria o
     * default, nunca o override do consumidor.
     */
    @Test
    void overrideDeResourcesDirAposApplyEhRefletidoNoCompiler() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(JtaGradlePlugin.class);

        JtaGradleExtension jta = project.getExtensions().getByType(JtaGradleExtension.class);
        jta.getResourcesDir().set(project.getLayout().getProjectDirectory().dir("config/jta"));

        JavaCompile compileJava = (JavaCompile) project.getTasks().getByName("compileJava");
        String expectedArg = "-Ajta.resourcesDir=" + new File(project.getProjectDir(), "config/jta").getAbsolutePath();

        assertTrue(resolveArgumentProviders(compileJava).contains(expectedArg),
                "esperava '" + expectedArg + "' em " + resolveArgumentProviders(compileJava));
    }

    @Test
    void resourcesDirECustomizavelViaExtensaoJta() {
        Project project = ProjectBuilder.builder().build();
        project.getPluginManager().apply("java");
        project.getPluginManager().apply(JtaGradlePlugin.class);

        JtaGradleExtension jta = project.getExtensions().getByType(JtaGradleExtension.class);
        jta.getResourcesDir().set(project.getLayout().getProjectDirectory().dir("config/jta"));

        assertEquals(new File(project.getProjectDir(), "config/jta"),
                jta.getResourcesDir().get().getAsFile());
    }

    private static List<String> resolveArgumentProviders(JavaCompile compileJava) {
        List<String> args = new ArrayList<>();
        for (CommandLineArgumentProvider provider : compileJava.getOptions().getCompilerArgumentProviders()) {
            provider.asArguments().forEach(args::add);
        }
        return args;
    }
}
