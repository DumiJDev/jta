package dev.jta.gradle;

import gg.jte.ContentType;
import gg.jte.gradle.JteExtension;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.file.Directory;
import org.gradle.api.tasks.compile.JavaCompile;

import java.nio.file.Path;

/**
 * Plugin Gradle minimo do JTA: aplica {@code gg.jte.gradle} (o plugin
 * oficial do JTE - este modulo NAO reimplementa nenhum codegen, so
 * delega) apontado para onde o {@code JtaAnnotationProcessor} escreve os
 * {@code .jte} gerados, e propaga a config de {@code jta { resourcesDir = ... } }
 * para todo {@link JavaCompile} como {@code -Ajta.resourcesDir} - o
 * equivalente Gradle do que o consumidor Maven ja tem que configurar a
 * mao (ver {@code jte-maven-plugin} no pom de qualquer starter/jta-demo).
 *
 * <p>Escopo deliberadamente minimo (scaffolding, nao um plugin
 * feature-complete): nao gerencia a dependencia do {@code jta-processor}
 * em si (o consumidor ainda declara
 * {@code annotationProcessor("io.github.dumijdev:jta-processor:...")} na
 * propria configuracao {@code annotationProcessor}, exatamente como
 * qualquer outro annotation processor no Gradle), nem oferece um
 * mecanismo de dev-loop proprio - para isso, ver {@code JtaTemplateEngineFactory}
 * em jta-runtime (dev-mode via {@code -Djta.dev=true}/{@code jta.config.toml}),
 * que funciona igual independente do build tool.
 */
public class JtaGradlePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        JtaGradleExtension jta = project.getExtensions().create("jta", JtaGradleExtension.class);
        jta.getResourcesDir().convention(project.getLayout().getProjectDirectory().dir("src/main/resources"));

        // delega para o plugin oficial do JTE - nenhum codegen/compilacao
        // de template e feito por este modulo.
        project.getPlugins().apply("gg.jte.gradle");

        JteExtension jte = project.getExtensions().getByType(JteExtension.class);
        jte.getContentType().set(ContentType.Html);
        // mesma logica do <sourceDirectory>/<targetDirectory> configurados
        // manualmente no jte-maven-plugin de todo modulo Maven consumidor
        // (ver jta-demo/pom.xml): aponta para onde o JtaAnnotationProcessor
        // escreve o .jte via Filer.createResource(SOURCE_OUTPUT, ...) -
        // build/generated/sources/annotations/java/main no layout padrao
        // do Gradle - e compila para dentro do output de classes do
        // sourceSet main, para o TemplateEngine pre-compilado (producao)
        // conseguir carregar via classloader depois.
        jte.getSourceDirectory().set(generatedTemplatesDir(project));
        jte.getTargetDirectory().set(mainClassesDir(project));
        jte.precompile();

        // getCompilerArgs() e uma List<String> comum (nao Property/ListProperty) -
        // adicionar direto aqui capturaria o valor DEFAULT de jta.resourcesDir
        // no momento em que este apply() roda, que e ANTES do bloco
        // `jta { resourcesDir = ... }` do build script do consumidor sequer
        // executar (plugins {} e aplicado primeiro; a extensao e configurada
        // depois, como qualquer outra instrucao do script). getCompilerArgumentProviders()
        // e o mecanismo lazy correto do Gradle para isso - resolve
        // jta.getResourcesDir() so na hora de montar a linha de comando do
        // compilador, ja com qualquer override do consumidor aplicado.
        project.getTasks().withType(JavaCompile.class).configureEach(task ->
                task.getOptions().getCompilerArgumentProviders().add(() -> java.util.List.of(
                        "-Ajta.resourcesDir=" + jta.getResourcesDir().get().getAsFile().getAbsolutePath())));
    }

    private static Path generatedTemplatesDir(Project project) {
        Directory dir = project.getLayout().getBuildDirectory()
                .dir("generated/sources/annotations/java/main/jta-templates").get();
        return dir.getAsFile().toPath();
    }

    private static Path mainClassesDir(Project project) {
        Directory dir = project.getLayout().getBuildDirectory().dir("classes/java/main").get();
        return dir.getAsFile().toPath();
    }
}
