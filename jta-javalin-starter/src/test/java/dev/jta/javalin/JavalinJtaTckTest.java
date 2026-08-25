package dev.jta.javalin;

import dev.jta.tck.AbstractJtaTck;
import dev.jta.tck.JtaAdapterHarness;

/** Roda o TCK compartilhado (jta-tck) contra o adaptador Javalin. */
class JavalinJtaTckTest extends AbstractJtaTck {

    @Override
    protected JtaAdapterHarness createHarness() {
        return new JavalinAdapterHarness();
    }
}
