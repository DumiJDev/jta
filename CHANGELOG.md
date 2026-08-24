# Changelog

Histórico de decisões e mudanças, em ordem cronológica inversa. O
`README.md` foca no estado atual do projeto; este arquivo guarda o
raciocínio por trás de como ele chegou lá.

## 2026-08-24 — Revisão de segurança (OWASP Top 10) e upgrades de dependências

- **Revisão completa mapeada ao OWASP Top 10 (2021)** — ver
  [`SECURITY.md`](./SECURITY.md) para o detalhe de cada achado.
- **CVE real corrigida:** `jte` estava fixado em `3.1.15`, que tem uma
  vulnerabilidade de XSS publicada
  ([CVE-2025-23026](https://github.com/casid/jte/security/advisories/GHSA-vh22-6c6h-rm8q)) —
  atualizado para `3.1.16`.
- **Spring Boot atualizado para `3.5.16`** (era `3.3.4`) — validado com
  `mvn verify` completo (20 testes, `BUILD SUCCESS`).
- **htmx pinado em `4.0.0-beta6`** (era `2.0.4`), com SRI (`integrity`/
  `crossorigin`) calculado contra o arquivo real servido pelo CDN. Decisão
  de risco aceita conscientemente — htmx 4 ainda não tem release estável;
  quem preferir a série 2.x pode sobrescrever `[htmx] cdn_url` em
  `jta.config.toml`.
- **Logging de eventos de segurança** (OWASP A09): negação de autorização
  (`JtaSecurityEnforcer`) e tentativa de invocar uma ação fora do
  allowlist (`JtaActionController`) agora geram log em `WARN` via SLF4J —
  antes eram silenciosas.
- **`JtaExceptionHandler`** (novo): captura exceções internas do JTA antes
  de vazarem detalhes crus (valor bruto do request, nome de classe/campo)
  numa resposta HTTP.
- **`SecurityRegressionTest`** (novo, em `jta-demo`): trava contra
  regressão os achados críticos do `SECURITY.md` com requisições HTTP
  reais contra a aplicação — allowlist de ações, mass assignment, e
  escaping automático de HTML (XSS) em contexto de texto e de atributo.
- Corrigido um teste de integração que estava quebrando `mvn verify`
  silenciosamente (`ContadorIntegrationTest` esperava uma regra CSS que
  não existe mais desde a migração do `Contador` para `styleUrl()`
  externo).
- SRI também documentado/aplicado ao HTMX; Tailwind CDN deliberadamente
  sem SRI (URL não versionada).

## Rodada anterior — layouts, i18n, null-safety, CLI, segurança declarativa, SSE

Do backlog original do documento de arquitetura, os itens que faltavam:

- **Null-safety** (`{{ campo? }}`/`{{ campo! }}`): campos anotados com
  qualquer `@Nullable` (detectado pelo nome simples da anotação — funciona
  com JSpecify, `javax.annotation`, JetBrains, sem forçar uma dependência
  específica) exigem sufixo explícito; `{{ campo }}` puro num campo
  `@Nullable` é erro de compilação. `?` gera fallback vazio, `!` gera
  `Objects.requireNonNull` com mensagem clara.
- **i18n com verificação estática** (`{{ 'chave' | translate }}`): a chave
  é validada contra `messages.properties` em compile-time (com sugestão
  "você quis dizer X?"). `dev.jta.core.Translations` faz o lookup em
  runtime via `ResourceBundle` puro do JDK, com fallback `???chave???`
  para chave ausente em runtime.
- **GraalVM**: o processor emite
  `META-INF/native-image/dev.jta/jta-generated/reflect-config.json`
  automaticamente, listando cada componente processado — sem isso, um
  binário nativo quebraria em runtime já que `JtaComponentInvoker` usa
  reflection extensivamente.
- **CLI de scaffolding** (`jta-cli`, novo módulo): `jta init <nome>` gera
  um projeto Maven+Spring Boot completo com um componente de exemplo;
  `jta new component <Nome>` gera um componente na pasta atual, inferindo
  o pacote a partir do caminho (`src/main/java/...`). Zero dependências.
- **Segurança declarativa** (`@RequiresRole`/`@AllowAnonymous`): se
  `jta.config.toml` configurar `[security] roles_enum`, cada role é
  validada contra as constantes desse enum em compile-time (com sugestão
  "você quis dizer X?"); `@RequiresRole` + `@AllowAnonymous` juntos é erro
  (contraditório). Aplicação em runtime via `JtaSecurityEnforcer`, contra
  `SecurityContextHolder` do Spring Security.
- **SSE** (`@Sse`): endpoint que re-renderiza o componente periodicamente
  (polling por intervalo, não push orientado a evento — limitação
  documentada) e transmite para todos os clientes conectados via
  `JtaSseController` (`SseEmitter` do Spring MVC).
- **Componentes de comunidade**: seletor derivado do FQN nunca colide +
  `@Use` para alias, mais `jta new component` da CLI para distribuição
  (copiar código, não depender de um pacote publicado).

## Rodada anterior — templates externos, layout, validação, persistência real

- **`templateUrl()`/`styleUrl()` externos**: `@AComponent(templateUrl = "Nome.jta")`/
  `styleUrl = "Nome.css")` leem de `src/main/resources/jta-templates/<pacote>/`
  em vez de exigir tudo inline.
- **`[selector]` de `jta.config.toml` aplicado de verdade**:
  `strip_domain_prefix`/`separator` lidos pelo processor em compile-time.
- **Navegação sem reload completo**: `<body hx-boost="true">` no
  `PageShellRenderer` — todo link/form sem `hx-*` próprio vira navegação
  HTMX progressiva automaticamente.
- **Jakarta Validation**: constraints (`@NotBlank`, `@Email`, etc.) direto
  nos campos públicos do componente. Opcional por design — injetado via
  `ObjectProvider<Validator>`, não quebra quem não usa
  `spring-boot-starter-validation`.
- **Design de base no `PageShellRenderer`**: tipografia, nav, botões,
  formulários e cartões estilizados globalmente.
- **Persistência real (JPA + H2)**: `Produto` como entidade JPA de
  verdade, `ProdutoRepository` (Spring Data), seed via `data.sql`.
- **`Redirect`**: lançado de dentro de uma ação para navegar após ela
  terminar — o starter responde com `HX-Redirect`, que o HTMX segue como
  navegação de página inteira.
- **Hook de inicialização (`init()`)**: convenção — método público
  `void init()` sem argumentos, chamado após popular path/query params e
  antes de renderizar (GET) ou validar (ação).
- **CRUD completo do catálogo**: criar/editar/excluir produtos.
- **`@Layout` + `<router-outlet/>` de verdade**: composição em runtime —
  a página é renderizada primeiro, o HTML vira o parâmetro `content` do
  template do layout (`$unsafe{content}`).
- **Feature flag do TailwindCSS**: `[features] tailwindcss = true` troca
  o design system embutido pelo Tailwind (CDN "Play"), mutuamente
  exclusivos com o CSS base.

## Fundação — MVP inicial

- `@AComponent`, `@Route`, `@Use` (anotações).
- Derivação de seletor canônico a partir do FQN (`SelectorDerivation`) +
  seletor explícito com detecção de colisão em compile-time.
- Transformação de template regex-based: `{{ campo }}`, `{{ metodo() }}`,
  `(evento)="acao()"` → HTMX, atributo de escopo `data-jta-component`.
- Validação de bindings contra campos/métodos reais da classe, com
  sugestão "você quis dizer X?" (distância de Levenshtein).
- `ComponentRegistry` em runtime, agregando `components.json` de
  múltiplos jars, com detecção de colisão de seletor entre bibliotecas
  independentes.
- `jta-spring-boot-starter`: `JtaActionController`, `JtaRouteRegistrar`,
  `JtaComponentInvoker`, `PageShellRenderer`.
- `CssScoper`: prefixa cada regra top-level do `style()` com
  `[data-jta-component="<selector>"]` — isolamento sem Shadow DOM.
- `JtaConfig` (zero dependências): leitor mínimo de `jta.config.toml`.
- Path params (`@Route("/produtos/{id}")`) validados em compile-time
  contra um campo público real, extraídos em runtime via
  `HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE`.
- Query params populados também no GET inicial (não só em ações HTMX).
- Integração real com Spring: DI de `@Service` via construtor;
  `JtaComponentInvoker` falha alto se o componente for bean Spring sem
  `@Scope("prototype")`.

Bugs de integração encontrados e corrigidos ao longo do caminho estão
detalhados em [`TROUBLESHOOTING.md`](./TROUBLESHOOTING.md).
