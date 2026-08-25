package dev.jta.core;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Wrapper fino, null-safe, sobre {@link URLEncoder#encode(String, java.nio.charset.Charset)},
 * usado exclusivamente pelo {@code JtaAnnotationProcessor} para envolver
 * TODA expressao dinamica (nao-literal) embutida na query string gerada
 * para argumentos de acao (ver documento de arquitetura, secao de
 * argumentos em acoes).
 *
 * <p><b>Por que isso existe (obrigatorio, nao opcional):</b> sem
 * URL-encoding, um valor de campo de texto livre contendo
 * {@code &action=outraCoisa} injetaria parametros extras na propria URL
 * de acao ja montada pelo template gerado - uma vulnerabilidade de
 * injecao nova que estaria sendo introduzida por esta feature. O
 * processor SEMPRE envolve toda expressao dinamica com este helper antes
 * de gerar a URL; literais (strings conhecidas em compile-time) nao
 * precisam, ja que sao url-encoded uma unica vez, estaticamente, no
 * proprio processor.
 */
public final class UrlEncoding {

    private UrlEncoding() {
    }

    public static String encode(Object value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }
}
