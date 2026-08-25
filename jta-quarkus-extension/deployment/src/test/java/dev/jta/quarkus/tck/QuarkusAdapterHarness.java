package dev.jta.quarkus.tck;

import dev.jta.core.SelectorDerivation;
import dev.jta.tck.ActionProbe;
import dev.jta.tck.HttpProbe;
import dev.jta.tck.JtaAdapterHarness;
import dev.jta.tck.JtaFeature;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Harness do TCK para o adaptador Quarkus - o servidor real (Vert.x
 * embutido) ja e subido por {@link QuarkusJtaTckTest} (via
 * {@code QuarkusUnitTest}, que faz a augmentation completa da extensao -
 * runtime + deployment cooperando de verdade, nao mockado) antes de
 * {@link #start()} ser chamado, entao {@link #start()}/{@link #stop()}
 * aqui sao no-ops - mesmo padrao de {@code SpringAdapterHarness}
 * (jta-spring-boot-starter), onde o framework hospedeiro tambem controla
 * o ciclo de vida do servidor.
 */
final class QuarkusAdapterHarness implements JtaAdapterHarness {

    /** Ver {@code QuarkusJtaTckTest#TEST} - porta fixa via {@code quarkus.http.test-port}. */
    static final int PORT = 8083;

    private static final Set<JtaFeature> SUPPORTED = EnumSet.of(
            JtaFeature.ROUTING,
            JtaFeature.ACTIONS,
            JtaFeature.ACTION_ALLOWLIST,
            JtaFeature.ROLE_AUTHORIZATION,
            JtaFeature.SSE
            // I18N deliberadamente fora: dev.jta.core.Translations (Fase 3,
            // fora do escopo desta stream) resolve o ResourceBundle via
            // ResourceBundle.getBundle(nome, locale), que usa o classloader
            // da classe chamadora (jta-core, camada "base" do
            // QuarkusClassLoader) em vez do classloader de contexto da
            // thread de requisicao (camada da aplicacao, onde
            // messages.properties de fato vive) - achado real rodando o TCK
            // do Quarkus pela primeira vez (toda chave de traducao caia no
            // fallback "???chave???"). Mesma familia do bug ja corrigido em
            // JtaPageDispatcher/JtaActionDispatcher/SseHub, mas o consertar
            // aqui exigiria mexer em Translations.java (Fase 3/locale, de
            // outra stream) - documentado como lacuna conhecida em vez de
            // silenciar via SUPPORTED indevido.
    );

    @Override
    public String adapterName() {
        return "Quarkus";
    }

    @Override
    public String skipReason(JtaFeature feature) {
        if (feature == JtaFeature.I18N) {
            return "Quarkus ainda nao suporta I18N: dev.jta.core.Translations resolve o ResourceBundle "
                    + "pelo classloader da classe chamadora (camada base do QuarkusClassLoader), nao pelo "
                    + "classloader de contexto da thread de requisicao (camada da aplicacao) - corrigir exige "
                    + "mexer em Translations.java, fora do escopo desta stream (Fase 3/locale).";
        }
        return JtaAdapterHarness.super.skipReason(feature);
    }

    @Override
    public void start() {
        // servidor ja rodando - ver javadoc da classe.
    }

    @Override
    public void stop() {
        // ciclo de vida do Vert.x embutido e gerenciado pelo proprio QuarkusUnitTest.
    }

    @Override
    public Set<JtaFeature> supportedFeatures() {
        return SUPPORTED;
    }

    private String baseUrl() {
        return "http://localhost:" + PORT;
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
