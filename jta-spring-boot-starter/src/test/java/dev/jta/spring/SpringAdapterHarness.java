package dev.jta.spring;

import dev.jta.core.SelectorDerivation;
import dev.jta.tck.ActionProbe;
import dev.jta.tck.HttpProbe;
import dev.jta.tck.JtaAdapterHarness;
import dev.jta.tck.JtaFeature;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Harness do TCK para o adaptador Spring - o servidor real (Tomcat
 * embutido, porta aleatoria) ja e subido pelo proprio {@code @SpringBootTest}
 * de {@link SpringJtaTckTest} antes de {@link #start()} ser chamado, entao
 * {@link #start()}/{@link #stop()} aqui sao no-ops: o unico trabalho deste
 * harness e traduzir a porta injetada por {@code @LocalServerPort} nas
 * URLs de cada probe.
 */
final class SpringAdapterHarness implements JtaAdapterHarness {

    private static final Set<JtaFeature> SUPPORTED = EnumSet.of(
            JtaFeature.ROUTING,
            JtaFeature.ACTIONS,
            JtaFeature.ACTION_ALLOWLIST,
            JtaFeature.ROLE_AUTHORIZATION,
            JtaFeature.SSE,
            JtaFeature.I18N
    );

    private final int port;

    SpringAdapterHarness(int port) {
        this.port = port;
    }

    @Override
    public String adapterName() {
        return "Spring";
    }

    @Override
    public void start() {
        // servidor ja rodando - ver javadoc da classe.
    }

    @Override
    public void stop() {
        // ciclo de vida do contexto e do Tomcat embutido e gerenciado pelo proprio @SpringBootTest.
    }

    @Override
    public Set<JtaFeature> supportedFeatures() {
        return SUPPORTED;
    }

    private String baseUrl() {
        return "http://localhost:" + port;
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
