package dev.jta.quarkus.tck;

import dev.jta.tck.AbstractJtaTck;
import dev.jta.tck.JtaAdapterHarness;
import io.quarkus.test.QuarkusUnitTest;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.importer.ExplodedImporter;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.nio.file.Path;

/**
 * Roda o TCK compartilhado (jta-tck) contra o adaptador Quarkus.
 *
 * <p>Ao contrario dos outros 3 adaptadores (onde o proprio modulo depende
 * do host e um {@code @SpringBootTest}/servidor embutido simples ja
 * basta), testar uma EXTENSAO Quarkus exige rodar a augmentation completa
 * (as {@code BuildStep}s do modulo {@code deployment}, incluindo
 * {@code JtaProcessor}, cooperando com o {@code @Recorder} do modulo
 * {@code runtime}) - por isso este teste vive aqui, no modulo deployment
 * (que ja depende do runtime; o inverso criaria um ciclo no reactor), e
 * usa {@link QuarkusUnitTest} (de {@code quarkus-junit5-internal}, ja
 * declarado no pom deste modulo) em vez do {@code @QuarkusTest} comum.
 *
 * <p>{@link #TEST} importa o {@code target/test-classes} deste modulo
 * inteiro (fixtures {@link Contador}/{@link AdminPage}/{@link Placar}/
 * {@link Saudacao} ja compiladas, os templates {@code .jte} ja
 * pre-compilados pelo {@code jte-maven-plugin}, {@code messages.properties}
 * e o {@code META-INF/jta/components.json} gerado pelo processor) como o
 * "artefato da aplicacao" a ser aumentado - a alternativa (listar cada
 * classe/recurso individualmente) exigiria conhecer de antemao os nomes
 * de classe gerados pelo JTE, que sao um detalhe de implementacao do
 * plugin.
 */
class QuarkusJtaTckTest extends AbstractJtaTck {

    @RegisterExtension
    static final QuarkusUnitTest TEST = new QuarkusUnitTest()
            .setArchiveProducer(() -> ShrinkWrap.create(ExplodedImporter.class, "jta-tck-quarkus.jar")
                    .importDirectory(Path.of(System.getProperty("user.dir"), "target", "test-classes").toFile())
                    .as(JavaArchive.class))
            // porta fixa (em vez de aleatoria) - QuarkusAdapterHarness precisa
            // saber a URL antes de qualquer callback do JUnit expor a porta
            // real, o que QuarkusUnitTest (diferente de @QuarkusTest com
            // @TestHTTPResource) nao oferece de forma simples.
            .overrideConfigKey("quarkus.http.test-port", String.valueOf(QuarkusAdapterHarness.PORT));

    @Override
    protected JtaAdapterHarness createHarness() {
        return new QuarkusAdapterHarness();
    }
}
