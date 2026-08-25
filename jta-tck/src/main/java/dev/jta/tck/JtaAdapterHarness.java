package dev.jta.tck;

import java.util.Set;

/**
 * O que cada adaptador (Spring, Javalin, standalone, Quarkus) precisa
 * fornecer ao TCK: um servidor real rodando com fixtures conhecidas, quais
 * {@link JtaFeature} suporta hoje, e onde estao os endpoints que provam
 * cada feature suportada.
 *
 * <p>Um metodo de probe (ex: {@link #routingProbe()}) so precisa devolver
 * algo valido se a {@link JtaFeature} correspondente estiver em
 * {@link #supportedFeatures()} - {@link AbstractJtaTck} nunca chama um
 * probe de feature nao suportada (o teste correspondente vira um skip
 * nomeado via {@link org.junit.jupiter.api.Assumptions} antes de chegar
 * la).
 */
public interface JtaAdapterHarness {

    /** Nome curto do adaptador, usado no relatorio/matriz (ex: {@code "Spring"}). */
    String adapterName();

    /** Sobe o servidor real com as fixtures deste adaptador. */
    void start() throws Exception;

    /** Encerra o servidor. Deve ser seguro chamar mesmo se {@link #start()} falhou parcialmente. */
    void stop() throws Exception;

    /** Features que este adaptador suporta hoje - unica fonte de verdade para o que o TCK verifica de verdade vs. pula. */
    Set<JtaFeature> supportedFeatures();

    /**
     * Motivo do skip para uma feature fora de {@link #supportedFeatures()} -
     * aparece na mensagem do teste "abortado" (visivel no relatorio do
     * Surefire) e na matriz de compatibilidade gerada.
     */
    default String skipReason(JtaFeature feature) {
        return adapterName() + " ainda nao suporta " + feature.name() + " (" + feature.description() + ").";
    }

    /** GET esperado devolver 200 com o corpo contendo {@link #routingExpectedMarker()}. */
    HttpProbe routingProbe();

    String routingExpectedMarker();

    /** POST esperado devolver 200 com o corpo (fragmento re-renderizado) contendo {@link #actionExpectedMarker()}. */
    ActionProbe actionProbe();

    String actionExpectedMarker();

    /** URL de acao com um nome de acao real do Java mas nunca declarado como acao JTA (ex: {@code ?action=hashCode}) - espera 404. */
    String actionAllowlistUrl();

    /** GET de uma pagina {@code @RequiresRole}, sem nenhuma autenticacao - espera 403. */
    String roleProtectedUrl();

    /** GET (mantido como stream) do endpoint {@code @Sse} - espera ao menos uma linha {@code data:} contendo {@link #sseExpectedMarker()}. */
    HttpProbe sseProbe();

    String sseExpectedMarker();

    /** GET de uma pagina que usa {@code {{ 'chave' | translate }}} - espera 200 com o corpo contendo {@link #i18nExpectedMarker()}. */
    HttpProbe i18nProbe();

    String i18nExpectedMarker();
}
