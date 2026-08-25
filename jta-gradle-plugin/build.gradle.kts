plugins {
    `java-gradle-plugin`
    id("java-library")
    `maven-publish`
}

group = "io.github.dumijdev"
version = "0.1.0-SNAPSHOT"

// Mesmo alvo de bytecode/linguagem dos modulos Maven do JTA (ver
// <maven.compiler.release>21</maven.compiler.release> no pom.xml raiz) -
// nao ha motivo pra este modulo divergir.
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

// Versao do jte-gradle-plugin (artefato de codegen/precompile do JTE para
// Gradle) intencionalmente proxima da versao gg.jte:jte usada pelos
// modulos Maven (ver <jte.version> no pom.xml raiz) - as duas nao sao a
// mesma coordenada nem tem o mesmo ciclo de release, mas divergir demais
// arrisca o codegen deste plugin gerar `.java` incompativel com o
// jte-runtime que a app Gradle consumidora traz transitivamente via
// jta-runtime. Atualizar as duas juntas ao fazer bump de qualquer uma.
val jteGradlePluginVersion = "3.2.4"

dependencies {
    // implementation (nao compileOnly): este plugin aplica gg.jte.gradle
    // programaticamente via project.getPlugins().apply(...) dentro do seu
    // proprio apply() - precisa estar no classpath de runtime do plugin,
    // nao so de compilacao.
    implementation("gg.jte:jte-gradle-plugin:$jteGradlePluginVersion")
    // jte-gradle-plugin nao expoe gg.jte.ContentType (tipo de jte-runtime)
    // transitivamente como api - declarado aqui so pra JtaGradlePlugin
    // conseguir referenciar ContentType.Html. Mesma versao do
    // jte-gradle-plugin acima de proposito (evita duas versoes de
    // jte-runtime coexistindo no classpath de execucao deste plugin) -
    // NAO precisa bater com <jte.version> do pom.xml raiz, que e a versao
    // que a app CONSUMIDORA usa em runtime, um classpath completamente
    // separado do classpath de build deste plugin.
    implementation("gg.jte:jte-runtime:$jteGradlePluginVersion")

    testImplementation(platform("org.junit:junit-bom:5.12.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

gradlePlugin {
    // Namespace io.github.dumijdev (nao dev.jta): mesmo raciocinio da
    // escolha de groupId Maven (io.github.dumijdev) no pom.xml raiz - o
    // Gradle Plugin Portal tambem exige prova de posse do namespace do id
    // do plugin, e io.github.<usuario> e verificado automaticamente contra
    // a conta GitHub do publisher. O pacote Java continua dev.jta.gradle
    // de proposito, para bater com a convencao de pacote (dev.jta.*) usada
    // em todo o resto do codebase - namespace do plugin Gradle e nome de
    // pacote Java sao coisas independentes, mesma distincao ja feita entre
    // groupId Maven e pacote Java.
    plugins {
        create("jta") {
            id = "io.github.dumijdev.jta"
            implementationClass = "dev.jta.gradle.JtaGradlePlugin"
            displayName = "JTA Gradle Plugin"
            description = "Aplica gg.jte.gradle e configura as opcoes do annotation processor do JTA " +
                    "automaticamente, para um consumidor Gradle nao precisar hand-wire -Ajta.resourcesDir."
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
