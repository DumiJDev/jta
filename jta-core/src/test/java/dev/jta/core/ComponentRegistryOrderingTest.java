package dev.jta.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regressao do bug real encontrado na auditoria: {@link ComponentRegistry}
 * guardava paginas num {@link java.util.HashMap}, cuja ordem de iteracao
 * nao e a de insercao e nao e estavel entre JVMs. Adaptadores cujo router
 * e "first-match-wins por ordem de registro" (Vert.x, no
 * jta-quarkus-extension) registravam rotas nessa ordem arbitraria - se
 * {@code /produtos/{id}} calhasse de ser registrada antes de
 * {@code /produtos/novo}, a segunda ficava silenciosamente inalcancavel.
 *
 * <p>{@link ComponentRegistry#pages()} agora ordena por especificidade
 * (menos variaveis de path primeiro) direto na fonte, para que nenhum
 * adaptador precise de reimplementar isso por conta propria - o
 * jta-standalone ja o fazia, mas os outros nao tinham como saber que
 * precisavam.
 */
class ComponentRegistryOrderingTest {

    @Test
    void pagesComeOrderedByPathSpecificity() {
        // Deliberadamente fora de ordem alfabetica/de insercao, para nao
        // dar sorte com a ordem que o HashMap por acaso produziria.
        ComponentMetadata comIdVariavel = page("dev.jta.demo.ProdutoDetalhe", "produto-detalhe", "/produtos/{id}");
        ComponentMetadata semVariavel = page("dev.jta.demo.ProdutoNovo", "produto-novo", "/produtos/novo");
        ComponentMetadata duasVariaveis = page("dev.jta.demo.MatriculaAluno", "matricula-aluno", "/alunos/{alunoId}/turmas/{turmaId}");

        ComponentRegistry registry = loadFrom(List.of(comIdVariavel, semVariavel, duasVariaveis));

        List<String> ordem = registry.pages().stream().map(ComponentMetadata::routePath).collect(Collectors.toList());

        assertEquals(List.of("/produtos/novo", "/produtos/{id}", "/alunos/{alunoId}/turmas/{turmaId}"), ordem,
                "rotas com menos variaveis de path devem vir primeiro, para nao ficarem "
                        + "inalcancaveis atras de um padrao mais generico num router first-match-wins");
    }

    @Test
    void tiesAreBrokenByRoutePathForStableOrdering() {
        ComponentMetadata b = page("dev.jta.demo.B", "b", "/b/{id}");
        ComponentMetadata a = page("dev.jta.demo.A", "a", "/a/{id}");

        ComponentRegistry registry = loadFrom(List.of(b, a));

        List<String> ordem = registry.pages().stream().map(ComponentMetadata::routePath).collect(Collectors.toList());

        assertEquals(List.of("/a/{id}", "/b/{id}"), ordem,
                "com o mesmo numero de variaveis, a ordem deve ser deterministica (pelo path), "
                        + "nao a ordem de insercao arbitraria de um HashMap");
    }

    private static ComponentMetadata page(String fqn, String selector, String routePath) {
        return new ComponentMetadata(
                fqn, selector, false, routePath,
                List.of(), selector + ".jte", null, false, null,
                List.of(), false, null, 0L, List.of());
    }

    /**
     * Simula {@code ComponentRegistry.loadFromClasspath} sem depender de um
     * classpath/jar real: escreve o JSON via {@link ComponentMetadataIo} e
     * usa reflection para popular os dois mapas internos, exatamente como o
     * metodo de fabrica publico faria a partir de
     * {@code META-INF/jta/components.json}. Evita duplicar toda a logica de
     * parsing de {@link ComponentMetadataIo} so para testar a ordenacao.
     */
    private static ComponentRegistry loadFrom(List<ComponentMetadata> metadata) {
        try {
            String json = ComponentMetadataIo.toJson(metadata);
            var classLoader = new ClassLoaderWithSingleResource(json);
            return ComponentRegistry.loadFromClasspath(classLoader);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** ClassLoader minimo que serve um unico recurso a partir de bytes em memoria. */
    private static final class ClassLoaderWithSingleResource extends ClassLoader {
        private final byte[] content;

        ClassLoaderWithSingleResource(String json) {
            super(null);
            this.content = json.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public java.util.Enumeration<java.net.URL> getResources(String name) throws java.io.IOException {
            if (!"META-INF/jta/components.json".equals(name)) {
                return java.util.Collections.emptyEnumeration();
            }
            java.net.URL url = new java.net.URL(null, "test:///components.json", new java.net.URLStreamHandler() {
                @Override
                protected java.net.URLConnection openConnection(java.net.URL u) {
                    return new java.net.URLConnection(u) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public java.io.InputStream getInputStream() {
                            return new ByteArrayInputStream(content);
                        }
                    };
                }
            });
            return java.util.Collections.enumeration(List.of(url));
        }
    }
}
