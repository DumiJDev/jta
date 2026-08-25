package dev.jta.gradle;

import org.gradle.api.file.DirectoryProperty;

/**
 * Bloco {@code jta { ... }} do build script - deliberadamente minimo
 * (escopo deste corte e scaffolding, nao um plugin completo).
 *
 * <p>Gradle instancia esta classe via {@code project.getExtensions().create("jta", JtaGradleExtension.class)}
 * e implementa os getters abstratos automaticamente (managed properties) -
 * nenhum construtor/campo concreto e necessario aqui.
 */
public abstract class JtaGradleExtension {

    /**
     * Diretorio onde o {@code JtaAnnotationProcessor} deve procurar
     * recursos em compile-time ({@code jta.config.toml}, {@code jta-templates/**}
     * para {@code templateUrl()}/{@code styleUrl()}) - passado ao processor
     * via {@code -Ajta.resourcesDir}.
     *
     * <p>Default: {@code src/main/resources} do projeto. Existe porque,
     * diferente do Maven (onde o processor le de {@code target/classes} via
     * {@code StandardLocation.CLASS_OUTPUT}, assumindo que
     * {@code process-resources} ja copiou tudo para la antes de
     * {@code compile} rodar), o Gradle nao garante essa ordem dentro da
     * mesma execucao incremental de {@code compileJava} - apontar
     * diretamente para a pasta fonte evita depender dessa premissa.
     *
     * <p><b>Nota:</b> nesta versao do {@code jta-processor} (a arvore em
     * que este plugin foi escrito), a opcao {@code -Ajta.resourcesDir}
     * ainda NAO e lida pelo processor - ele so sabe ler via
     * {@code StandardLocation.CLASS_OUTPUT}, igual ao caminho Maven. Este
     * plugin ja passa a opcao (scaffolding correto, sem custo hoje - uma
     * {@code -A} option desconhecida e ignorada silenciosamente pelo
     * {@code javac}), pronta para o dia em que o processor ganhar esse
     * suporte (rastreado como um fix de outra fase do plano mestre) sem
     * exigir nenhuma mudanca no build script do consumidor.
     */
    public abstract DirectoryProperty getResourcesDir();
}
