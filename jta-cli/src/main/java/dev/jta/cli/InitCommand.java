package dev.jta.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Implementa {@code jta init <nome-do-projeto>}.
 *
 * <p>Gera um projeto Maven de modulo unico (nao o multi-modulo deste
 * repositorio - o dev consumidor nao precisa disso), com Spring Boot,
 * um componente de exemplo (contador, igual ao do documento de
 * arquitetura original) e a estrutura minima para rodar
 * {@code mvn spring-boot:run} imediatamente.
 *
 * <p><b>Limitacao honesta:</b> o {@code pom.xml} gerado referencia
 * {@code dev.jta:jta-core}/{@code jta-processor}/{@code jta-spring-boot-starter}
 * na versao {@code 0.1.0-SNAPSHOT} - como este projeto ainda nao esta
 * publicado no Maven Central, o dev precisa rodar {@code mvn install}
 * neste repositorio primeiro (para essas coordenadas ficarem no
 * repositorio Maven local) antes do projeto gerado conseguir resolver
 * as dependencias. O template deixa isso comentado explicitamente no
 * pom.xml gerado, para nao ser uma surpresa silenciosa.
 */
final class InitCommand {

    private InitCommand() {
    }

    static void run(String projectName) {
        Path root = Path.of(projectName);
        if (Files.exists(root)) {
            System.err.println("Erro: o diretorio '" + projectName + "' ja existe.");
            System.exit(1);
            return;
        }

        try {
            Files.createDirectories(root.resolve("src/main/java/com/example/app"));
            Files.createDirectories(root.resolve("src/main/resources"));

            write(root.resolve("pom.xml"), pomXml(projectName));
            write(root.resolve("src/main/java/com/example/app/Application.java"), applicationJava());
            write(root.resolve("src/main/java/com/example/app/Ola.java"), olaJava());
            write(root.resolve("src/main/resources/application.properties"), applicationProperties());

            System.out.println("Projeto criado em ./" + projectName);
            System.out.println();
            System.out.println("IMPORTANTE: se voce ainda nao tem dev.jta:jta-core/jta-processor/");
            System.out.println("jta-spring-boot-starter no seu repositorio Maven local, rode `mvn install`");
            System.out.println("no repositorio do JTA primeiro (este projeto ainda nao esta publicado).");
            System.out.println();
            System.out.println("Depois:");
            System.out.println("  cd " + projectName);
            System.out.println("  mvn spring-boot:run");
            System.out.println("  abrir http://localhost:8080/ola");
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao criar o projeto em " + root, e);
        }
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content);
    }

    private static String pomXml(String projectName) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0"
                         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
                    <modelVersion>4.0.0</modelVersion>

                    <groupId>com.example</groupId>
                    <artifactId>%s</artifactId>
                    <version>0.1.0-SNAPSHOT</version>
                    <packaging>jar</packaging>

                    <parent>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-starter-parent</artifactId>
                        <version>3.5.16</version>
                    </parent>

                    <properties>
                        <maven.compiler.release>21</maven.compiler.release>
                        <!-- dev.jta:* ainda nao esta publicado no Maven Central -
                             rode `mvn install` no repositorio do JTA antes de tentar
                             buildar este projeto, para essas coordenadas ficarem
                             disponiveis no seu repositorio Maven local (~/.m2). -->
                        <jta.version>0.1.0-SNAPSHOT</jta.version>
                        <jte.version>3.1.16</jte.version>
                    </properties>

                    <dependencies>
                        <dependency>
                            <groupId>org.springframework.boot</groupId>
                            <artifactId>spring-boot-starter-web</artifactId>
                        </dependency>
                        <dependency>
                            <groupId>dev.jta</groupId>
                            <artifactId>jta-core</artifactId>
                            <version>${jta.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>dev.jta</groupId>
                            <artifactId>jta-spring-boot-starter</artifactId>
                            <version>${jta.version}</version>
                        </dependency>
                        <dependency>
                            <groupId>dev.jta</groupId>
                            <artifactId>jta-processor</artifactId>
                            <version>${jta.version}</version>
                            <scope>provided</scope>
                        </dependency>
                    </dependencies>

                    <build>
                        <plugins>
                            <plugin>
                                <groupId>org.apache.maven.plugins</groupId>
                                <artifactId>maven-compiler-plugin</artifactId>
                                <configuration>
                                    <parameters>true</parameters>
                                    <annotationProcessorPaths>
                                        <path>
                                            <groupId>dev.jta</groupId>
                                            <artifactId>jta-processor</artifactId>
                                            <version>${jta.version}</version>
                                        </path>
                                    </annotationProcessorPaths>
                                </configuration>
                            </plugin>
                            <plugin>
                                <groupId>gg.jte</groupId>
                                <artifactId>jte-maven-plugin</artifactId>
                                <version>${jte.version}</version>
                                <executions>
                                    <execution>
                                        <phase>process-classes</phase>
                                        <goals><goal>precompile</goal></goals>
                                    </execution>
                                </executions>
                                <configuration>
                                    <sourceDirectory>${project.build.directory}/generated-sources/annotations/jta-templates</sourceDirectory>
                                    <targetDirectory>${project.build.outputDirectory}</targetDirectory>
                                    <contentType>Html</contentType>
                                </configuration>
                            </plugin>
                            <plugin>
                                <groupId>org.springframework.boot</groupId>
                                <artifactId>spring-boot-maven-plugin</artifactId>
                                <executions>
                                    <execution>
                                        <goals><goal>repackage</goal></goals>
                                    </execution>
                                </executions>
                                <configuration>
                                    <mainClass>com.example.app.Application</mainClass>
                                </configuration>
                            </plugin>
                        </plugins>
                    </build>
                </project>
                """.formatted(projectName);
    }

    private static String applicationJava() {
        return """
                package com.example.app;

                import org.springframework.boot.SpringApplication;
                import org.springframework.boot.autoconfigure.SpringBootApplication;

                @SpringBootApplication
                public class Application {
                    public static void main(String[] args) {
                        SpringApplication.run(Application.class, args);
                    }
                }
                """;
    }

    private static String olaJava() {
        return """
                package com.example.app;

                import dev.jta.core.AComponent;
                import dev.jta.core.Route;

                @Route("/ola")
                @AComponent(
                    template = "<div><h1>{{ titulo }}</h1>"
                             + "<p>Voce clicou <span id=\\"valor\\">{{ valor }}</span> vez(es).</p>"
                             + "<input type=\\"hidden\\" name=\\"valor\\" value=\\"{{ valor }}\\"/>"
                             + "<button (click)=\\"incrementar()\\">Clique aqui</button></div>",
                    style = "h1 { color: #2563eb; }"
                )
                public class Ola {
                    public String titulo = "Ola, JTA!";
                    public int valor = 0;

                    public void incrementar() {
                        valor++;
                    }
                }
                """;
    }

    private static String applicationProperties() {
        return """
                server.port=8080
                """;
    }
}
