package dev.jta.cli;

/**
 * Entry point da CLI de scaffolding do JTA.
 *
 * <pre>
 *   jta init &lt;nome-do-projeto&gt;           - cria um projeto novo (pom.xml + exemplo)
 *   jta new component &lt;NomeDoComponente&gt; - cria um componente na pasta atual
 * </pre>
 *
 * <p>Programa Java puro (zero dependencias) - so escreve arquivos em
 * disco, nao precisa do pipeline JTE/Spring rodando para funcionar. Isso
 * o torna testavel diretamente com {@code java}/{@code javac}, sem
 * precisar de Maven Central nem de um projeto Spring Boot real.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            System.exit(1);
            return;
        }

        switch (args[0]) {
            case "init" -> {
                if (args.length < 2) {
                    System.err.println("Uso: jta init <nome-do-projeto>");
                    System.exit(1);
                    return;
                }
                InitCommand.run(args[1]);
            }
            case "new" -> {
                if (args.length < 3 || !args[1].equals("component")) {
                    System.err.println("Uso: jta new component <NomeDoComponente>");
                    System.exit(1);
                    return;
                }
                NewComponentCommand.run(args[2]);
            }
            default -> {
                printUsage();
                System.exit(1);
            }
        }
    }

    private static void printUsage() {
        System.out.println("""
                Uso:
                  jta init <nome-do-projeto>            Cria um novo projeto JTA (Maven + Spring Boot + exemplo)
                  jta new component <NomeDoComponente>   Cria um novo componente na pasta atual
                """);
    }
}
