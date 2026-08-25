package dev.jta.runtime;

import dev.jta.runtime.upload.UploadedFile;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code populateUploads} popula campos publicos do tipo {@link UploadedFile}
 * a partir de partes de arquivo ja extraidas pelo adaptador - restrito a
 * {@code uploadFields} (mesma allowlist de {@code populateFromParams}), sem
 * nenhuma coercao de tipo (o valor ja chega como {@link UploadedFile}).
 */
class ComponentInvokerUploadTest {

    private final ComponentInvoker invoker = new ComponentInvoker(new ReflectionComponentFactory());

    public static final class ComUpload {
        public UploadedFile avatar;
        public String nome = "";
    }

    @Test
    void populaCampoDeUploadQuandoNaAllowlist() {
        ComUpload instance = new ComUpload();
        UploadedFile file = new UploadedFile("foto.png", "image/png", new byte[]{1, 2, 3});

        invoker.populateUploads(instance, Map.of("avatar", file), Set.of("avatar"));

        assertSame(file, instance.avatar);
    }

    @Test
    void ignoraCampoDeUploadForaDaAllowlist() {
        ComUpload instance = new ComUpload();
        UploadedFile file = new UploadedFile("foto.png", "image/png", new byte[]{1, 2, 3});

        invoker.populateUploads(instance, Map.of("avatar", file), Set.of());

        assertNull(instance.avatar);
    }

    @Test
    void semParteDeArquivoEnviadaCampoPermaneceNull() {
        ComUpload instance = new ComUpload();

        invoker.populateUploads(instance, Map.of(), Set.of("avatar"));

        assertNull(instance.avatar);
    }

    @Test
    void naoInterfereComCamposDeTextoComuns() {
        ComUpload instance = new ComUpload();
        UploadedFile file = new UploadedFile("foto.png", "image/png", new byte[]{1});

        invoker.populateUploads(instance, Map.of("avatar", file), Set.of("avatar"));
        invoker.populateFromParams(instance, Map.of("nome", new String[]{"Ana"}), Set.of("nome"));

        assertSame(file, instance.avatar);
        assertEquals("Ana", instance.nome);
    }
}
