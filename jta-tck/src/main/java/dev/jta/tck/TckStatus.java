package dev.jta.tck;

/** Resultado de uma feature TCK para um adaptador, na matriz de compatibilidade gerada. */
public enum TckStatus {
    /** Feature declarada suportada e o teste correspondente passou. */
    SUPORTADO,
    /** Feature nao declarada em {@link JtaAdapterHarness#supportedFeatures()} - skip nomeado, nao falha. */
    IGNORADO,
    /** Feature declarada suportada mas o teste correspondente falhou. */
    FALHOU
}
