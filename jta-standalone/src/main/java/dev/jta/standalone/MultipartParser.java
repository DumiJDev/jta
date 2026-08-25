package dev.jta.standalone;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser minimo de {@code multipart/form-data} (RFC 7578), proprio -
 * {@code com.sun.net.httpserver.HttpServer} (o unico framework por baixo
 * de jta-standalone) nao tem NENHUM parsing de multipart embutido, ao
 * contrario de Spring/Javalin/Quarkus, que herdam isso do container
 * servlet/Vert.x por baixo.
 *
 * <p><b>Decisao de nao usar uma lib de terceiros (ex: commons-fileupload2):</b>
 * o plano-mestre deste projeto recomendava justamente isso, isolado a este
 * modulo - avaliado, mas a unica versao publicada e um milestone
 * (2.0.0-M5, sem release estavel) cuja API generica (nao-servlet) nao
 * pode ser verificada offline com confianca dentro do orcamento desta
 * entrega. O formato multipart/form-data em si e simples e bem
 * especificado o suficiente (delimitacao por boundary, sem aninhamento)
 * para um parser proprio, pequeno e totalmente coberto por teste, sem o
 * risco de integrar uma API de um milestone as cegas - mesma filosofia
 * zero-dependencias ja usada para JSON em jta-core ({@code JsonIo}).
 *
 * <p><b>Escopo deliberado:</b> so o suficiente para o caso real que um
 * {@code <input type="file">} de formulario HTML produz - um nivel de
 * partes, sem multipart/mixed aninhado (obsoleto em navegadores modernos
 * para upload de multiplos arquivos, que hoje usam varias partes com o
 * mesmo {@code name} no nivel superior em vez disso).
 */
final class MultipartParser {

    private static final Pattern CONTENT_TYPE_BOUNDARY = Pattern.compile("boundary=\"?([^\";]+)\"?");
    private static final Pattern CONTENT_DISPOSITION_NAME = Pattern.compile("name=\"([^\"]*)\"");
    private static final Pattern CONTENT_DISPOSITION_FILENAME = Pattern.compile("filename=\"([^\"]*)\"");
    private static final Pattern CONTENT_TYPE_HEADER = Pattern.compile("(?i)^Content-Type:\\s*(.+)$");

    record Part(String name, String filename, String contentType, byte[] content) {
        boolean isFile() {
            return filename != null;
        }
    }

    private MultipartParser() {
    }

    /** @return o valor de {@code boundary=...} do header {@code Content-Type}, ou {@code null} se ausente/nao-multipart. */
    static String extractBoundary(String contentTypeHeader) {
        if (contentTypeHeader == null) {
            return null;
        }
        Matcher m = CONTENT_TYPE_BOUNDARY.matcher(contentTypeHeader);
        return m.find() ? m.group(1) : null;
    }

    static List<Part> parse(byte[] body, String boundary) {
        List<Part> parts = new ArrayList<>();
        byte[] delimiter = ("--" + boundary).getBytes(StandardCharsets.US_ASCII);

        List<Integer> delimiterPositions = findAll(body, delimiter);
        for (int i = 0; i < delimiterPositions.size() - 1; i++) {
            int partStart = delimiterPositions.get(i) + delimiter.length;
            int partEnd = delimiterPositions.get(i + 1);
            if (partEnd <= partStart) {
                continue;
            }
            // cada parte comeca com "\r\n" (fim da linha do delimitador) e
            // termina com "\r\n" antes do proximo delimitador - o ultimo
            // delimitador e sempre "--boundary--" (fim), nunca uma parte real.
            byte[] segment = trimLeadingCrLf(body, partStart, partEnd);
            if (isFinalDelimiter(body, partStart)) {
                break;
            }
            Part part = parsePart(segment);
            if (part != null) {
                parts.add(part);
            }
        }
        return parts;
    }

    private static boolean isFinalDelimiter(byte[] body, int afterDelimiter) {
        return afterDelimiter + 1 < body.length && body[afterDelimiter] == '-' && body[afterDelimiter + 1] == '-';
    }

    private static byte[] trimLeadingCrLf(byte[] body, int start, int end) {
        int from = start;
        if (from + 1 < end && body[from] == '\r' && body[from + 1] == '\n') {
            from += 2;
        }
        int to = end;
        if (to - 2 >= from && body[to - 2] == '\r' && body[to - 1] == '\n') {
            to -= 2;
        }
        byte[] out = new byte[to - from];
        System.arraycopy(body, from, out, 0, out.length);
        return out;
    }

    private static Part parsePart(byte[] segment) {
        int headerEnd = indexOf(segment, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII), 0);
        if (headerEnd < 0) {
            return null;
        }
        String headerText = new String(segment, 0, headerEnd, StandardCharsets.UTF_8);
        byte[] content = new byte[segment.length - (headerEnd + 4)];
        System.arraycopy(segment, headerEnd + 4, content, 0, content.length);

        String name = null;
        String filename = null;
        String contentType = null;
        for (String line : headerText.split("\r\n")) {
            if (line.regionMatches(true, 0, "Content-Disposition:", 0, "Content-Disposition:".length())) {
                Matcher nameM = CONTENT_DISPOSITION_NAME.matcher(line);
                if (nameM.find()) {
                    name = nameM.group(1);
                }
                Matcher filenameM = CONTENT_DISPOSITION_FILENAME.matcher(line);
                if (filenameM.find()) {
                    filename = filenameM.group(1);
                }
            } else {
                Matcher ctM = CONTENT_TYPE_HEADER.matcher(line.trim());
                if (ctM.matches()) {
                    contentType = ctM.group(1).trim();
                }
            }
        }
        if (name == null) {
            return null;
        }
        return new Part(name, filename, contentType, content);
    }

    private static List<Integer> findAll(byte[] haystack, byte[] needle) {
        List<Integer> positions = new ArrayList<>();
        int from = 0;
        int found;
        while ((found = indexOf(haystack, needle, from)) >= 0) {
            positions.add(found);
            from = found + needle.length;
        }
        return positions;
    }

    private static int indexOf(byte[] haystack, byte[] needle, int from) {
        outer:
        for (int i = from; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
