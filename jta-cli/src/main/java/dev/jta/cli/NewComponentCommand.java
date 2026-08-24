package dev.jta.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Implementa {@code jta new component <NomeDoComponente>}.
 *
 * <p>Escreve {@code <Nome>.java} na pasta atual, com um esqueleto de
 * {@code @AComponent} pronto para editar. O pacote e inferido da propria
 * localizacao no disco (procurando {@code src/main/java} no caminho
 * absoluto do diretorio atual e usando o que vem depois disso) - mesma
 * convencao que ferramentas de scaffolding como o Spring Initializr ou
 * `ng generate` usam. Se o diretorio atual nao estiver dentro de
 * {@code src/main/java} (ex: rodando fora de um projeto Maven/Gradle
 * padrao), o arquivo e gerado sem declaracao de pacote, com um aviso.
 */
final class NewComponentCommand {

    private NewComponentCommand() {
    }

    static void run(String componentName) {
        Path cwd = Path.of("").toAbsolutePath();
        String packageName = inferPackage(cwd);

        Path target = Path.of(componentName + ".java");
        if (Files.exists(target)) {
            System.err.println("Erro: " + target + " ja existe.");
            System.exit(1);
            return;
        }

        String content = componentJava(packageName, componentName);
        try {
            Files.writeString(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao criar " + target, e);
        }

        System.out.println("Criado " + target.toAbsolutePath());
        if (packageName == null) {
            System.out.println("Aviso: nao encontrei 'src/main/java' no caminho atual, entao o arquivo foi "
                    + "gerado sem declaracao de pacote. Mova-o para o pacote certo e adicione a linha "
                    + "'package ...;' manualmente se precisar.");
        }
    }

    /**
     * Encontra {@code src/main/java} no caminho absoluto do diretorio
     * atual e converte o restante em nome de pacote (barras -> pontos).
     * Retorna {@code null} se {@code src/main/java} nao aparecer no
     * caminho.
     */
    static String inferPackage(Path cwd) {
        Path marker = null;
        for (Path p = cwd; p != null; p = p.getParent()) {
            if (endsWithSrcMainJava(p)) {
                marker = p;
                break;
            }
        }
        if (marker == null || marker.equals(cwd)) {
            return marker == null ? null : "";
        }
        Path relative = marker.relativize(cwd);
        return relative.toString().replace(java.io.File.separatorChar, '.');
    }

    private static boolean endsWithSrcMainJava(Path p) {
        Path parent1 = p.getParent();
        Path parent2 = parent1 != null ? parent1.getParent() : null;
        return p.getFileName() != null && p.getFileName().toString().equals("java")
                && parent1 != null && parent1.getFileName() != null && parent1.getFileName().toString().equals("main")
                && parent2 != null && parent2.getFileName() != null && parent2.getFileName().toString().equals("src");
    }

    private static String componentJava(String packageName, String name) {
        String packageLine = (packageName == null || packageName.isBlank()) ? "" : "package " + packageName + ";\n\n";
        return packageLine + """
                import dev.jta.core.AComponent;

                @AComponent(
                    template = "<div><h1>%s</h1></div>",
                    style = ""
                )
                public class %s {
                    // campos publicos = estado do componente, referenciaveis via {{ campo }}
                    // metodos publicos void = acoes, referenciaveis via (evento)="acao()"
                    // metodos publicos com retorno = metodos de template, referenciaveis via {{ metodo() }}
                }
                """.formatted(name, name);
    }
}
