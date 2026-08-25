package dev.jta.standalone;

import dev.jta.core.SelectorDerivation;
import dev.jta.tck.ActionProbe;
import dev.jta.tck.HttpProbe;
import dev.jta.tck.JtaAdapterHarness;
import dev.jta.tck.JtaFeature;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Harness do TCK para o adaptador standalone - sobe um {@link JtaHttpServer}
 * real (porta aleatoria), reusando as mesmas fixtures dos outros testes
 * deste modulo ({@link Contador}, {@link Placar}, {@code fixtures.AdminPage}).
 */
final class StandaloneAdapterHarness implements JtaAdapterHarness {

    private static final Set<JtaFeature> SUPPORTED = EnumSet.of(
            JtaFeature.ROUTING,
            JtaFeature.ACTIONS,
            JtaFeature.ACTION_ALLOWLIST,
            JtaFeature.ROLE_AUTHORIZATION,
            JtaFeature.SSE,
            JtaFeature.I18N
    );

    private JtaHttpServer server;

    @Override
    public String adapterName() {
        return "Standalone";
    }

    @Override
    public void start() {
        server = JtaHttpServer.create(0, JtaStandaloneConfig.create());
        server.start();
    }

    @Override
    public void stop() {
        server.stop();
    }

    @Override
    public Set<JtaFeature> supportedFeatures() {
        return SUPPORTED;
    }

    private String baseUrl() {
        return "http://localhost:" + server.port();
    }

    private String selector(Class<?> type) {
        return SelectorDerivation.derive(type.getName());
    }

    @Override
    public HttpProbe routingProbe() {
        return new HttpProbe(baseUrl() + "/contador");
    }

    @Override
    public String routingExpectedMarker() {
        return ">0<";
    }

    @Override
    public ActionProbe actionProbe() {
        return new ActionProbe(baseUrl() + "/__jta/action/" + selector(Contador.class) + "?action=incrementar",
                Map.of("valor", "5"));
    }

    @Override
    public String actionExpectedMarker() {
        return ">6<";
    }

    @Override
    public String actionAllowlistUrl() {
        return baseUrl() + "/__jta/action/" + selector(Contador.class) + "?action=hashCode";
    }

    @Override
    public String roleProtectedUrl() {
        return baseUrl() + "/admin";
    }

    @Override
    public HttpProbe sseProbe() {
        return new HttpProbe(baseUrl() + "/sse/placar");
    }

    @Override
    public String sseExpectedMarker() {
        return ">7<";
    }

    @Override
    public HttpProbe i18nProbe() {
        return new HttpProbe(baseUrl() + "/saudacao");
    }

    @Override
    public String i18nExpectedMarker() {
        return "Ola do TCK";
    }
}
