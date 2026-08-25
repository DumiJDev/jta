package dev.jta.javalin;

import dev.jta.core.SelectorDerivation;
import dev.jta.tck.ActionProbe;
import dev.jta.tck.HttpProbe;
import dev.jta.tck.JtaAdapterHarness;
import dev.jta.tck.JtaFeature;
import io.javalin.Javalin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Harness do TCK para o adaptador Javalin - sobe um {@link Javalin} real
 * (porta aleatoria) com {@link JtaJavalin#register}, reusando as mesmas
 * fixtures dos outros testes deste modulo ({@link Contador}, {@link Placar}).
 */
final class JavalinAdapterHarness implements JtaAdapterHarness {

    private static final Set<JtaFeature> SUPPORTED = EnumSet.of(
            JtaFeature.ROUTING,
            JtaFeature.ACTIONS,
            JtaFeature.ACTION_ALLOWLIST,
            JtaFeature.ROLE_AUTHORIZATION,
            JtaFeature.SSE,
            JtaFeature.I18N
    );

    private Javalin app;

    @Override
    public String adapterName() {
        return "Javalin";
    }

    @Override
    public void start() {
        app = Javalin.create();
        JtaJavalin.register(app);
        app.start(0);
    }

    @Override
    public void stop() {
        app.stop();
    }

    @Override
    public Set<JtaFeature> supportedFeatures() {
        return SUPPORTED;
    }

    private String baseUrl() {
        return "http://localhost:" + app.port();
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
        // Accept: text/event-stream e obrigatorio - o SseHandler nativo do
        // Javalin (bridgeado por JtaJavalin) so entra em modo SSE se o
        // cliente declarar isso, o mesmo header que um EventSource de
        // navegador ja manda sozinho.
        return new HttpProbe(baseUrl() + "/sse/placar", Map.of("Accept", "text/event-stream"));
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
