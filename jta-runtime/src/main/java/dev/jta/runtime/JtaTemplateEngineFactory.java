package dev.jta.runtime;

import dev.jta.core.JtaConfig;
import gg.jte.ContentType;
import gg.jte.TemplateEngine;
import gg.jte.resolve.DirectoryCodeResolver;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Monta o {@link TemplateEngine} do JTE, escolhendo entre o modo
 * pre-compilado (producao) e um modo de dev-loop que recompila um
 * template individual sob demanda quando o {@code .jte} correspondente
 * muda no disco - sem precisar recompilar/reiniciar a aplicacao inteira.
 *
 * <h2>Por que nao era so trocar para {@code TemplateEngine.create(...)}</h2>
 *
 * <p>Todo adaptador (Spring/Javalin/standalone/Quarkus) hoje monta o
 * engine com {@link TemplateEngine#createPrecompiled(ContentType)} - ver a
 * nota em {@code JtaAutoConfiguration} (jta-spring-boot-starter): o modo
 * "on-demand" ingenuo ({@code TemplateEngine.create(CodeResolver, ContentType)},
 * 2 argumentos) compila o {@code .jte} usando um classpath isolado que NAO
 * enxerga as classes da propria aplicacao, e falha em runtime com algo
 * como {@code "package dev.jta.demo does not exist"} assim que o template
 * gerado referencia o tipo do componente no {@code @param}. Este factory
 * usa a sobrecarga de 4 argumentos ({@link TemplateEngine#create(gg.jte.CodeResolver, Path, ContentType, ClassLoader)},
 * passando o classloader da aplicacao) mais {@link TemplateEngine#setClassPath},
 * apontado para {@code java.class.path} do processo atual - a combinacao
 * que faz o compilador interno do JTE enxergar os tipos da aplicacao ao
 * recompilar um template modificado.
 *
 * <h2>O que o dev-loop cobre, e o que NAO cobre</h2>
 *
 * <p>Cobre: editar o CONTEUDO de um template ({@code .jte} gerado, ou o
 * {@code .jta}/{@code .css} externo referenciado via {@code templateUrl()}/
 * {@code styleUrl()} - ver {@code readExternalResource} em
 * {@code JtaAnnotationProcessor}) e ver a mudanca refletida na proxima
 * requisicao, sem restart da JVM: o {@link DirectoryCodeResolver} aponta
 * direto para {@code target/generated-sources/annotations/jta-templates}
 * (o diretorio onde o processor escreve o {@code .jte} via
 * {@code Filer.createResource(SOURCE_OUTPUT, ...)}), e o JTE detecta o
 * timestamp do arquivo mudou e recompila so aquele template.
 *
 * <p>NAO cobre, sozinho: uma mudanca que precise o
 * {@code JtaAnnotationProcessor} rodar de novo para regenerar o
 * {@code .jte} (ex: um novo campo referenciado no template, uma mudanca
 * de {@code templateUrl()} para outro arquivo, um novo {@code @AComponent}).
 * Isso ainda exige uma recompilacao Java (que roda o processor de novo) -
 * mas nao um restart da JVM, se combinado com um watcher de arquivo/build
 * continuo do lado de fora (IDE "build automatico", ou
 * {@code mvn compiler:compile} disparado por um watcher, ou o futuro
 * {@code jta-gradle-plugin} com o build continuo do Gradle). Este factory
 * so cuida do lado "JTE recarrega o template compilado", nao do lado
 * "o processor regenerou o template" - as duas metades juntas sao o
 * dev-loop completo.
 *
 * <h2>Como ligar</h2>
 *
 * <p>Desligado por padrao (sempre {@link TemplateEngine#createPrecompiled}
 * a menos que explicitamente ligado) - nenhum consumidor existente muda de
 * comportamento so por atualizar o jta-runtime. Duas formas de ligar,
 * nesta ordem de precedencia:
 * <ol>
 *   <li>system property {@code -Djta.dev=true} (conveniente para ligar
 *       num unico comando/run configuration, sem tocar em arquivo
 *       versionado);</li>
 *   <li>{@code [dev] enabled = true} em {@code jta.config.toml} (para
 *       deixar o dev-mode como default do projeto num profile/perfil
 *       local nao commitado, ou explicitamente documentado para o time).</li>
 * </ol>
 * O diretorio de templates ({@code [dev] templates_dir}/{@code -Djta.dev.templatesDir})
 * e o diretorio de trabalho do compilador do JTE ({@code [dev] class_dir}/
 * {@code -Djta.dev.classDirectory}) tambem sao configuraveis, com defaults
 * corretos para o layout padrao do Maven - um consumidor Gradle (layout
 * {@code build/generated/sources/annotations/...}) precisa sobrescrever
 * {@code templates_dir} explicitamente ate o {@code jta-gradle-plugin}
 * cuidar disso automaticamente.
 */
public final class JtaTemplateEngineFactory {

    /** {@code -Djta.dev=true|false} - maior precedencia que {@code [dev] enabled} de {@code jta.config.toml}. */
    public static final String DEV_MODE_PROPERTY = "jta.dev";
    public static final String TEMPLATES_DIR_PROPERTY = "jta.dev.templatesDir";
    public static final String CLASS_DIR_PROPERTY = "jta.dev.classDirectory";

    private static final String DEFAULT_TEMPLATES_DIR = "target/generated-sources/annotations/jta-templates";
    private static final String DEFAULT_CLASS_DIR = "target/jta-dev-classes";

    private JtaTemplateEngineFactory() {
    }

    /** Monta o engine usando o classloader de contexto da thread atual como {@code parentClassLoader} do modo dev. */
    public static TemplateEngine create(JtaConfig config) {
        return create(config, Thread.currentThread().getContextClassLoader());
    }

    /**
     * Monta o engine. Em modo producao (default), identico a
     * {@code TemplateEngine.createPrecompiled(ContentType.Html)} - o que
     * todo starter ja fazia antes deste factory existir. Em modo dev
     * (ver {@link #isDevModeEnabled}), monta um {@link DirectoryCodeResolver}
     * sobre {@link #resolveTemplatesDir} com o classpath/classloader da
     * aplicacao (ver javadoc da classe para o porque disso ser necessario).
     */
    public static TemplateEngine create(JtaConfig config, ClassLoader classLoader) {
        if (!isDevModeEnabled(config)) {
            return TemplateEngine.createPrecompiled(ContentType.Html);
        }

        Path templatesDir = Paths.get(resolveTemplatesDir(config));
        Path classDirectory = Paths.get(resolveClassDirectory(config));

        TemplateEngine engine = TemplateEngine.create(
                new DirectoryCodeResolver(templatesDir), classDirectory, ContentType.Html, classLoader);
        engine.setClassPath(runtimeClassPathEntries());
        return engine;
    }

    /** {@code -Djta.dev} tem precedencia sobre {@code [dev] enabled} de {@code jta.config.toml}; ambos ausentes = producao. */
    public static boolean isDevModeEnabled(JtaConfig config) {
        String override = System.getProperty(DEV_MODE_PROPERTY);
        if (override != null) {
            return Boolean.parseBoolean(override);
        }
        return config.getBoolean("dev", "enabled", false);
    }

    private static String resolveTemplatesDir(JtaConfig config) {
        String override = System.getProperty(TEMPLATES_DIR_PROPERTY);
        if (override != null) {
            return override;
        }
        return config.getString("dev", "templates_dir", DEFAULT_TEMPLATES_DIR);
    }

    private static String resolveClassDirectory(JtaConfig config) {
        String override = System.getProperty(CLASS_DIR_PROPERTY);
        if (override != null) {
            return override;
        }
        return config.getString("dev", "class_dir", DEFAULT_CLASS_DIR);
    }

    /**
     * {@code java.class.path} do processo atual, quebrado nas entradas
     * individuais - e o que {@link TemplateEngine#setClassPath} espera
     * para o compilador interno do JTE resolver os tipos da aplicacao
     * referenciados no {@code @param} de um template recompilado em
     * modo dev (ver javadoc da classe).
     */
    private static List<String> runtimeClassPathEntries() {
        String classPath = System.getProperty("java.class.path", "");
        if (classPath.isBlank()) {
            return List.of();
        }
        return List.of(classPath.split(File.pathSeparator));
    }
}
