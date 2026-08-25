package dev.jta.tck;

/** Resultado de uma unica {@link JtaFeature} para um adaptador, com o detalhe (motivo do skip, ou mensagem de falha). */
public record TckResult(TckStatus status, String detail) {

    public static TckResult supported() {
        return new TckResult(TckStatus.SUPORTADO, null);
    }

    public static TckResult skipped(String reason) {
        return new TckResult(TckStatus.IGNORADO, reason);
    }

    public static TckResult failed(String message) {
        return new TckResult(TckStatus.FALHOU, message);
    }
}
