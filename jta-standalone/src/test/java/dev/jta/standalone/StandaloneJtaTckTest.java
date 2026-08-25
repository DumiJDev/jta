package dev.jta.standalone;

import dev.jta.tck.AbstractJtaTck;
import dev.jta.tck.JtaAdapterHarness;

/** Roda o TCK compartilhado (jta-tck) contra o adaptador standalone. */
class StandaloneJtaTckTest extends AbstractJtaTck {

    @Override
    protected JtaAdapterHarness createHarness() {
        return new StandaloneAdapterHarness();
    }
}
