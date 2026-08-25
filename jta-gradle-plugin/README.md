# jta-gradle-plugin

Plugin Gradle minimo do JTA - scaffolding, nao um plugin feature-completo.

## Como isto se encaixa no resto do repositorio

Todo outro modulo deste repositorio (`jta-core`, `jta-processor`,
`jta-runtime`, os starters, `jta-test`, `jta-demo`, ...) e um projeto
Maven, construido pelo reactor do `pom.xml` na raiz do repositorio.

Este modulo e a **unica excecao**: e um projeto **Gradle** independente,
de proposito **fora** do reactor Maven (nao aparece em `<modules>` do
`pom.xml` raiz, e nao tem `pom.xml` proprio). Um plugin Gradle e
publicado como um artefato Gradle (tipicamente no Gradle Plugin Portal),
com seu proprio ciclo de build/teste/publicacao - misturar isso no
reactor Maven nao traria beneficio nenhum e complicaria os dois builds
sem necessidade.

## O que o plugin faz

Aplicado num projeto Gradle que usa JTA (`plugins { id("io.github.dumijdev.jta") }`):

1. Aplica `gg.jte.gradle` (o plugin **oficial** do JTE) - este modulo nao
   reimplementa nenhum codegen/compilacao de template, so delega.
2. Aponta o `sourceDirectory`/`targetDirectory` do JTE para onde o
   `JtaAnnotationProcessor` escreve o `.jte` gerado
   (`build/generated/sources/annotations/java/main/jta-templates`) e para
   o output de classes do sourceSet `main`, e seleciona o estagio
   `precompile()` (equivalente ao goal `precompile` do
   `jte-maven-plugin`, ja usado por todo modulo Maven consumidor - ver
   `jta-demo/pom.xml`).
3. Propaga `jta { resourcesDir = ... }` (default: `src/main/resources`)
   como a opcao de compilador `-Ajta.resourcesDir` para todo
   `JavaCompile` - o equivalente Gradle do que um consumidor Maven ja
   ganha de graca hoje (o processor le recursos via
   `StandardLocation.CLASS_OUTPUT`, assumindo que `process-resources` ja
   rodou antes de `compile` - uma premissa que o Gradle nao garante do
   mesmo jeito).

**Nota honesta:** na arvore em que este plugin foi escrito,
`JtaAnnotationProcessor` ainda **nao le** a opcao `-Ajta.resourcesDir` -
ele so sabe ler via `StandardLocation.CLASS_OUTPUT` (mesmo caminho
Maven). Passar a opcao hoje e inofensivo (uma `-A` desconhecida e
ignorada silenciosamente pelo `javac`) e deixa o plugin pronto para o dia
em que o processor ganhar esse suporte - rastreado como um fix de outra
fase do plano mestre do projeto, fora do escopo deste modulo.

## O que fica de fora deste corte

- Nao gerencia a dependencia do `jta-processor` em si - o consumidor
  ainda declara
  `annotationProcessor("io.github.dumijdev:jta-processor:<versao>")` na
  propria configuracao `annotationProcessor`, como qualquer outro
  annotation processor no Gradle.
- Nao oferece nenhum mecanismo de dev-loop proprio - para hot reload de
  template, ver `JtaTemplateEngineFactory` em `jta-runtime`
  (`-Djta.dev=true` ou `[dev] enabled = true` em `jta.config.toml`, ver
  README da raiz do repositorio), que funciona identico independente do
  build tool.
- Nao publica nada - so scaffolding de config, sem `mvn deploy`/publish
  real executado.

## Build e testes

```bash
cd jta-gradle-plugin
./gradlew build
```

Roda a compilacao Java 21 do plugin e a suite de testes unitarios
(`JtaGradlePluginTest`, via `ProjectBuilder` - aplica o plugin num
projeto Gradle in-memory e verifica que `gg.jte.gradle` foi aplicado, que
`JteExtension` recebeu a config esperada, e que
`-Ajta.resourcesDir` chega em todo `JavaCompile` de forma preguicosa
(reflete um `jta { resourcesDir = ... }` do consumidor, nao so o
default)).

Requer Gradle (o wrapper baixa a distribuicao certa automaticamente na
primeira execucao, se ainda nao estiver em cache local) e acesso a
Maven Central/Gradle Plugin Portal para resolver `gg.jte:jte-gradle-plugin`.

## Usar num projeto consumidor (Gradle)

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        // enquanto nao publicado no Gradle Plugin Portal:
        mavenLocal()
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    id("io.github.dumijdev.jta") version "0.1.0-SNAPSHOT"
}

dependencies {
    implementation("io.github.dumijdev:jta-core:0.1.0-SNAPSHOT")
    implementation("io.github.dumijdev:jta-runtime:0.1.0-SNAPSHOT")
    annotationProcessor("io.github.dumijdev:jta-processor:0.1.0-SNAPSHOT")
    // + o starter do host escolhido (Spring/Javalin/standalone/Quarkus)
}

jta {
    // opcional - default ja e src/main/resources
    resourcesDir = layout.projectDirectory.dir("src/main/resources")
}
```

`./gradlew publishToMavenLocal` (rodado dentro deste diretorio) publica o
plugin no repositorio Maven local, para um projeto consumidor local
testar via `mavenLocal()` antes de uma publicacao real no Gradle Plugin
Portal - nenhum publish real foi feito neste corte.
