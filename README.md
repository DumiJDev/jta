# JTA — Java Angular Template

[![build](https://github.com/DumiJDev/jta/actions/workflows/build.yml/badge.svg)](https://github.com/DumiJDev/jta/actions/workflows/build.yml)
[![license: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](./LICENSE)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot 3.5](https://img.shields.io/badge/Spring%20Boot-3.5-brightgreen.svg)](https://spring.io/projects/spring-boot)

**Componentes tipados, validados em compile-time, sobre [JTE](https://jte.gg/) + [HTMX](https://htmx.org/) — a experiência de desenvolvimento de um framework de componentes (estilo Angular), inteiramente em Java, sem escrever uma linha de JavaScript de aplicação.**

```java
@Route("/contador")
@AComponent(
    template = "<h1>{{ titulo }}</h1>"
             + "<p>{{ valor }}</p>"
             + "<button (click)=\"incrementar()\">+</button>",
    style = "h1 { color: #2563eb; }"
)
public class Contador {
    public String titulo = "Cliques";
    public int valor = 0;

    public void incrementar() {
        valor++;
    }
}
```

Isso é o componente inteiro. `JtaAnnotationProcessor` valida em
compile-time que `titulo`/`valor`/`incrementar()` existem de verdade na
classe (com sugestão "você quis dizer X?" se você digitar algo errado),
gera o `.jte`, escopa o CSS sem Shadow DOM, e o `jta-spring-boot-starter`
expõe a rota e o endpoint de ação — sem `@RestController`, sem
`@PathVariable`, sem uma linha de JS escrita à mão.

## Por que

- **Compile-time-first** — um binding pra um campo/método/ação que não
  existe é erro de build, não um bug descoberto em produção. Sugestão
  "você quis dizer X?" via distância de Levenshtein.
- **Seguro por padrão** — só campos que o template realmente referencia
  são bindáveis a partir da requisição (sem mass assignment); só ações
  declaradas (métodos `void` reais) são invocáveis via HTTP (sem RCE-like
  via reflection). Ver [`SECURITY.md`](./SECURITY.md).
- **Zero dependências no core** — `jta-core`/`jta-processor`/`jta-cli`
  compilam e testam com `javac` puro, sem nenhuma lib externa.
- **HTMX + JTE, não um framework paralelo** — `@if`/`@for` são JTE puro;
  o JTA só transforma o essencial (`{{ }}`, `(evento)="..."`) e sai da
  frente.
- **Null-safety, i18n e GraalVM de verdade** — `{{ campo? }}`/`{{ campo! }}`
  para campos `@Nullable`; `{{ 'chave' | translate }}` validado contra
  `messages.properties` em compile-time; `reflect-config.json` gerado
  automaticamente para `native-image`.

## Quick start

```bash
mvn -q install                     # instala jta-core/processor/starter/cli no seu ~/.m2
java -jar jta-cli/target/jta-cli.jar init meu-app
cd meu-app
mvn spring-boot:run
# abrir http://localhost:8080/ola
```

Ou explore o demo completo já incluído neste repositório:

```bash
mvn -q install
cd jta-demo
mvn spring-boot:run
# abrir http://localhost:8080/
```

## Arquitetura

| Módulo | O que faz | Dependências |
|---|---|---|
| `jta-core` | Anotações, `SelectorDerivation`, `ComponentRegistry`, `JtaConfig` | nenhuma |
| `jta-processor` | `JtaAnnotationProcessor` + `TemplateTransformer` + `CssScoper` | só `jta-core` |
| `jta-runtime` | Núcleo de runtime **agnóstico de framework web**: `ComponentInvoker`, `SecurityEnforcer`, `JtaActionDispatcher`/`JtaPageDispatcher`, `PageShellRenderer` | `jta-core`, JTE |
| `jta-cli` | `jta init`, `jta new component` | nenhuma |
| `jta-spring-boot-starter` | Adaptador fino do Spring MVC/Spring Security sobre `jta-runtime`: registro de rotas, controllers, SSE | `jta-runtime`, Spring Boot |
| `jta-demo` | App de exemplo — clínica veterinária (Tutores/Pets/Visitas/Veterinários), no espírito do Spring PetClinic | tudo acima |

Pipeline: `@AComponent` → `JtaAnnotationProcessor` (compile-time) →
`.jte` gerado → `jte-maven-plugin` (precompilado) → `jta-runtime`
(dispatch agnóstico de framework) → adaptador Spring MVC → navegador
(HTMX).

`jta-runtime` existe para que outros hosts (Quarkus, Javalin, standalone —
ver [`CHANGELOG.md`](./CHANGELOG.md)) precisem só de um adaptador fino
em cima dele, sem reimplementar os allowlists de segurança
(`ComponentMetadata.actions()`, `bindableFields()`) nem a checagem de
`@RequiresRole`.

## Demo incluído

Domínio único (clínica veterinária), no espírito do Spring PetClinic:

| Rota | Demonstra |
|---|---|
| `/` | Nav compartilhada via `@Layout`/`<router-outlet/>` |
| `/contador` | Estado simples, sem DI, CSS via `styleUrl()` externo |
| `/tutores` | Listagem via `@for`, persistência real (JPA + H2) |
| `/tutores/{id}` | Path param + DI + relação um-para-muitos (pets do tutor) |
| `/tutores/novo`, `/tutores/{id}/editar` | DI + Jakarta Validation + `Redirect` + `init()` |
| `/tutores/{tutorId}/pets/novo` | Criação **aninhada** — path param usado como FK |
| `/pets/{id}` | Ação que cria um registro filho (visita) direto da página de detalhe do pai |
| `/veterinarios` | Listagem aberta |
| `/veterinarios/novo`, `/veterinarios/{id}/editar` | Protegidas por `@RequiresRole("ADMIN")` — entre como `admin`/`admin` (ou `user`/`user` para ver o `403`) |
| `/tarefas` | `@if`/`@for` (JTE puro) + toggle de campo `boolean` |
| `/contato` | Jakarta Validation bloqueando a ação em dados inválidos |

## Segurança

O projeto passou por uma revisão de código orientada a segurança e, mais
recentemente, por uma revisão mapeada explicitamente ao **OWASP Top 10
(2021)** — com achados críticos (invocação arbitrária de método via
reflection, mass assignment) travados por testes de integração reais
(`SecurityRegressionTest`), não só por leitura de código. Ver
[`SECURITY.md`](./SECURITY.md) para o histórico completo de achados,
correções e o que ficou documentado como decisão de design (CSRF, IDOR,
limite de conexões SSE).

## Testando

```bash
./scripts/smoke-test.sh   # jta-core/jta-processor/jta-cli, sem Maven nem internet
mvn verify                # suíte completa, incluindo testes de integração e de segurança
```

CI (`.github/workflows/build.yml`) roda `mvn verify` a cada push/PR.

## Limitações honestas

- Layouts aninhados (`@Layout` usando outro `@Layout`) não são suportados.
- i18n usa `Locale.getDefault()` (locale da JVM), não o `Accept-Language`
  da requisição.
- SSE (`@Sse`) é polling por intervalo fixo, não push orientado a evento.
- Jakarta Validation não valida objetos aninhados (`@Valid` em cascata).
- Reidratação de estado só converte `String`/`int`/`long`/`double`/`boolean`.
- htmx 4 ainda não tem release estável — este projeto pina
  `4.0.0-beta6` conscientemente (ver [`SECURITY.md`](./SECURITY.md) e
  [`CHANGELOG.md`](./CHANGELOG.md)); sobrescreva `[htmx] cdn_url` em
  `jta.config.toml` se preferir a série 2.x.
- Só existe adaptador para Spring Boot ainda. `jta-runtime` já extraiu o
  núcleo agnóstico (ver "Arquitetura" acima) especificamente para reduzir
  esse gap — Quarkus/Javalin/standalone/plugin Gradle ficam mais baratos
  de construir agora, mas ainda não existem.

Histórico completo de decisões, features e bugs corrigidos:
[`CHANGELOG.md`](./CHANGELOG.md) · [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md).

## Licença

[MIT](./LICENSE)
