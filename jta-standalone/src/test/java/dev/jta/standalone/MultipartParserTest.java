package dev.jta.standalone;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultipartParserTest {

    @Test
    void extraiBoundaryDoContentType() {
        assertEquals("XYZ", MultipartParser.extractBoundary("multipart/form-data; boundary=XYZ"));
        assertEquals("XYZ", MultipartParser.extractBoundary("multipart/form-data; boundary=\"XYZ\""));
    }

    @Test
    void boundaryAusenteDevolveNull() {
        assertEquals(null, MultipartParser.extractBoundary("application/x-www-form-urlencoded"));
    }

    @Test
    void separaCampoDeTextoEParteDeArquivo() {
        String boundary = "----WebKitFormBoundaryXYZ";
        String body = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"titulo\"\r\n\r\n"
                + "Meu Titulo\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"avatar\"; filename=\"foto.png\"\r\n"
                + "Content-Type: image/png\r\n\r\n"
                + "conteudo-binario-fake\r\n"
                + "--" + boundary + "--\r\n";

        List<MultipartParser.Part> parts = MultipartParser.parse(body.getBytes(StandardCharsets.UTF_8), boundary);

        assertEquals(2, parts.size());
        MultipartParser.Part textPart = parts.get(0);
        assertEquals("titulo", textPart.name());
        assertFalse(textPart.isFile());
        assertEquals("Meu Titulo", new String(textPart.content(), StandardCharsets.UTF_8));

        MultipartParser.Part filePart = parts.get(1);
        assertEquals("avatar", filePart.name());
        assertTrue(filePart.isFile());
        assertEquals("foto.png", filePart.filename());
        assertEquals("image/png", filePart.contentType());
        assertEquals("conteudo-binario-fake", new String(filePart.content(), StandardCharsets.UTF_8));
    }

    @Test
    void preservaBytesBinariosDoArquivo() {
        String boundary = "b1";
        byte[] fileBytes = {0, 1, 2, (byte) 0xFF, (byte) 0x80, 10, 13};
        byte[] header = ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"f\"; filename=\"x.bin\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] footer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] body = new byte[header.length + fileBytes.length + footer.length];
        System.arraycopy(header, 0, body, 0, header.length);
        System.arraycopy(fileBytes, 0, body, header.length, fileBytes.length);
        System.arraycopy(footer, 0, body, header.length + fileBytes.length, footer.length);

        List<MultipartParser.Part> parts = MultipartParser.parse(body, boundary);

        assertEquals(1, parts.size());
        assertEquals(fileBytes.length, parts.get(0).content().length);
        for (int i = 0; i < fileBytes.length; i++) {
            assertEquals(fileBytes[i], parts.get(0).content()[i]);
        }
    }

    @Test
    void semPartesDevolveListaVazia() {
        String boundary = "b1";
        String body = "--" + boundary + "--\r\n";

        List<MultipartParser.Part> parts = MultipartParser.parse(body.getBytes(StandardCharsets.UTF_8), boundary);

        assertTrue(parts.isEmpty());
    }
}
