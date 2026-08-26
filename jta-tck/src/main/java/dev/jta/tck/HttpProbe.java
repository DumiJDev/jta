package dev.jta.tck;

import java.util.Map;

/**
 * Descreve uma requisicao GET que {@link AbstractJtaTck} deve fazer contra
 * o servidor de um adaptador, e o cabecalho extra (se algum) que a
 * requisicao precisa levar - ex: {@code Accept: text/event-stream}, que o
 * suporte SSE nativo do Javalin exige (ver {@code JtaJavalin}).
 */
public record HttpProbe(String url, Map<String, String> headers) {

    public HttpProbe(String url) {
        this(url, Map.of());
    }
}
