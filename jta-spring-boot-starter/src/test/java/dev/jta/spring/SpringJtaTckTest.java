package dev.jta.spring;

import dev.jta.tck.AbstractJtaTck;
import dev.jta.tck.JtaAdapterHarness;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Roda o TCK compartilhado (jta-tck) contra o adaptador Spring, sobre um Tomcat embutido real. */
@SpringBootTest(classes = TckSpringApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SpringJtaTckTest extends AbstractJtaTck {

    @LocalServerPort
    private int port;

    @Override
    protected JtaAdapterHarness createHarness() {
        return new SpringAdapterHarness(port);
    }
}
