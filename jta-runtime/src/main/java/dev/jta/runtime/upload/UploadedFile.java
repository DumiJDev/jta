package dev.jta.runtime.upload;

/**
 * Representacao minima e agnostica de framework de um arquivo enviado via
 * {@code multipart/form-data}, populada pelo runtime num campo publico
 * deste tipo (ver {@code ComponentInvoker#populateUploads},
 * {@code ComponentMetadata#uploadFields}) - nunca a partir de query
 * params/form fields de texto, so de partes de arquivo reais extraidas
 * pelo adaptador (ex: {@code JtaHttpServer} em jta-standalone,
 * {@code jakarta.servlet.http.Part} no Spring).
 *
 * @param filename    nome de arquivo original enviado pelo cliente (nao
 *                    confiavel para uso direto como path de filesystem -
 *                    quem persiste o conteudo deve sanitizar/gerar o
 *                    proprio nome)
 * @param contentType tipo MIME declarado pelo cliente (tambem nao
 *                    confiavel sem validacao adicional do lado do
 *                    consumidor, se a decisao de negocio depender disso)
 * @param content     bytes crus do arquivo
 */
public record UploadedFile(String filename, String contentType, byte[] content) {

    public boolean isEmpty() {
        return content == null || content.length == 0;
    }
}
