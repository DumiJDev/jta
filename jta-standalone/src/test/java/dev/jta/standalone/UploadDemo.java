package dev.jta.standalone;

import dev.jta.core.AComponent;
import dev.jta.core.Route;
import dev.jta.runtime.upload.UploadedFile;

/**
 * Fixture minimo para provar upload de arquivo via multipart/form-data
 * (ver {@code ComponentMetadata#uploadFields}, {@code MultipartParser}).
 */
@Route("/upload-demo")
@AComponent(template = "<main><span id=\"nome-arquivo\">{{ nomeArquivo() }}</span></main>")
public class UploadDemo {

    public UploadedFile avatar;

    public String nomeArquivo() {
        return avatar == null ? "nenhum" : avatar.filename();
    }

    public void enviar() {
        // acao vazia - so precisa existir para o dispatch popular 'avatar'
        // antes de re-renderizar (mesmo fluxo de qualquer outra acao).
    }
}
