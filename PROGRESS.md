# Estado da execução do plano-mestre JTA (sessão 2026-08-25)

Contexto: plano-mestre tem 6 fases (0-5). Antes desta sessão já estavam
commitados em `master`: fases 0, 1, 2, e a maior parte da fase 4
(composição @Use/@Input, hx-include, deteção de ciclos, argumentos em ações).

Auditoria desta sessão confirmou o que faltava e lançou 4 streams paralelos
(cada um em worktree isolado) para fechar o resto. Resultado abaixo.

## Streams concluídos e commitados (dentro dos próprios worktrees, NÃO em master)

1. **Fase 3 — Correção de dados** — commit `7c1846b`, branch
   `worktree-agent-a31e61b897ac32cfe`
   Worktree: `.claude/worktrees/agent-a31e61b897ac32cfe`
   - `ConverterRegistry` + `ConversionException` (jta-core): enum, LocalDate/LocalDateTime,
     BigDecimal, UUID, List/array, Optional.
   - `JtaAnnotationProcessor`: erro em compile-time para campos bindáveis sem conversor.
   - `ComponentInvoker`/`JtaActionDispatcher`/`JtaPageDispatcher`: erros de conversão
     viram erros de formulário (mesmo mapa que bean validation), já não fazem throw.
   - Multi-valor: campos `List<T>`/array recebem todos os valores; escalares passam
     a usar `values[values.length-1]` (era `values[0]`).
   - `LocaleContext` (thread-local) + `LocaleResolver`/`AcceptLanguageLocaleResolver`,
     ligado ao Spring adapter (`Accept-Language`, default configurável em
     `jta.config.toml [i18n] default_locale`). Javalin/standalone/Quarkus continuam
     com `Locale.getDefault()` (sem regressão, mas sem locale por-requisição ainda).
   - ⚠️ Este branch partiu do commit `b2927f5`, ANTERIOR às fases 2 e 4 em master —
     `CsrfRequest`/`JtaSession`/checks de argumentos de ação não existiam nesta base.
     Vai precisar de rebase/merge cuidadoso contra `master` atual.

2. **Fase 4 (resto) — Slots + Flash/upload/error-pages** — commit `74d684c`,
   branch `worktree-agent-a8ba07b0d0ad918c1`
   Worktree: `.claude/worktrees/agent-a8ba07b0d0ad918c1`
   - Slots: `<slot/>` / `<slot>fallback</slot>` em `TemplateTransformer`, projeção de
     conteúdo via `<tag>corpo</tag>`, aviso de slot não usado. Só slot default (sem
     nome) suportado — limitação MVP documentada no código.
   - `ComponentMetadata`: `hasSlot`, `isErrorPage`, `errorPageStatus`, `uploadFields`.
   - `Redirect.withFlashSuccess/withFlashError`, `FlashSupport`, protegido por
     `ReservedFieldNames` (nomes reservados não podem vir de query/form).
   - `MultipartParser` próprio (RFC 7578, zero deps) isolado em `jta-standalone`;
     upload wired em `ComponentInvoker` + Spring + standalone.
   - `JtaErrorPageRenderer` + `JtaErrorController` (Spring, com
     `@AutoConfiguration(before = ErrorMvcAutoConfiguration.class)` +
     `@ConditionalOnMissingBean` para não colidir com o `BasicErrorController` do Boot)
     e wiring equivalente no `JtaHttpServer` standalone.
   - Deliberadamente NÃO wired: Javalin e Quarkus ficaram sem upload/error-page
     (compila via overload de 6 argumentos preservado, mas sem a feature nova).
   - Build + testes: verde (`mvn -q compile`/`test` no reactor inteiro).

3. **Fase 5b — Ferramentas (jta-test, dev-loop, plugin Gradle, publicação Maven)**
   — commit `7f8955f`, branch `worktree-agent-afde64eca17eefb4a`
   Worktree: `.claude/worktrees/agent-afde64eca17eefb4a`
   - Novo módulo `jta-test` (`JtaTestHarness`, `JtaAssertions`, `TestCurrentUser`).
     CSRF/sessão ainda não mockáveis (documentado em `package-info.java`).
   - Dev-loop: `JtaTemplateEngineFactory` (jta-runtime) alterna
     `TemplateEngine.createPrecompiled` (default) vs `DirectoryCodeResolver`
     quando `-Djta.dev=true` ou `[dev] enabled=true` no `jta.config.toml`. Wired
     nos 4 adapters.
   - `jta-gradle-plugin`: projeto Gradle próprio (fora do reactor Maven), aplica
     `gg.jte.gradle` + propaga `-Ajta.resourcesDir`. Verificado com
     `./gradlew build --offline` (jte-gradle-plugin pinned em 3.2.4, única versão
     disponível offline — nota: `JtaAnnotationProcessor` neste branch ainda não lê
     essa flag, ficou documentado como gap).
   - Config de publicação Maven Central (só config, SEM deploy real): groupId
     `io.github.dumijdev` (recomendação do plano-mestre) propagado a todos os
     módulos, `licenses`/`scm`/`developers`/`distributionManagement` via
     Central Publishing Portal (`central.sonatype.com`, não o OSSRH legado).
   - ⚠️ Também partiu de `b2927f5` — mesmo aviso de rebase da fase 3.

## Stream NÃO concluído — precisa retomar

4. **Fase 5a — Extração do hub SSE + esqueleto de TCK**
   Worktree: `.claude/worktrees/agent-ad1ddf70d452c282b`
   Branch: `worktree-agent-ad1ddf70d452c282b`
   **NADA commitado ainda** — o worktree tem alterações não commitadas.

   Estado conhecido no momento em que foi interrompido (morto manualmente, sem
   ser erro):
   - `SseHub` (jta-runtime) criado, extraído do SSE só-Spring.
   - Javalin e standalone com wiring de SSE feito (`app.sse(...)` / `HttpExchange`
     mantido aberto com `text/event-stream`).
   - `JtaSseRouteHandler` (Quarkus) criado — verificar se está completo/correto.
   - Módulo `jta-tck/` criado, com harnesses de adapter e testes TCK para Javalin
     e standalone (`JavalinJtaTckTest`, `StandaloneJtaTckTest`).
   - A matriz de compatibilidade gerada já batia com o README no último check.
   - Última ação relatada: prestes a correr o build+testes completos do reactor
     antes de commitar — isto NÃO chegou a correr/confirmar.

   **Próximo passo:** retomar este worktree (não recriar do zero — há trabalho
   real por commitar), correr `mvn -q compile` + `mvn -q test` no reactor
   inteiro, corrigir o que falhar, e commitar com mensagem em PT seguindo o
   estilo do repo (ver `git log`). Evitar builds Maven longos via
   `run_in_background` + polling — isso causou 2 stalls anteriores nesta sessão;
   correr em foreground com timeout razoável.

## Depois de fechar a fase 5a: reconciliação

Nenhum destes 4 branches foi mergeado em `master`. Falta:
1. Terminar e commitar a fase 5a (acima).
2. Reconciliar os 4 branches contra o `master` atual — 3 deles (`7c1846b`,
   `74d684c` no branch da fase 4 é o único que partiu de cima do estado atual;
   `7c1846b` e `7f8955f` partiram do commit antigo `b2927f5`) vão ter conflitos
   reais de merge/rebase (mesmos ficheiros tocados por múltiplas fases:
   `ComponentMetadata`, `JtaAnnotationProcessor`, `pom.xml` raiz, etc.) — seguir
   o padrão já usado no histórico deste repo (`git log`: commits
   "Reconcilia fase X com fase Y").
3. Rodar o build completo (`mvn -q clean install` no reactor) depois de cada
   reconciliação.
4. Commitar a reconciliação final em `master` com mensagem em PT, estilo do
   repo.
5. Ainda por decidir/confirmar com o utilizador: publicação real no Maven
   Central (a config está pronta mas não foi testada com deploy real) e
   se se quer avançar SSE/upload/error-pages para Javalin e Quarkus (ficaram
   documentados como gap, não implementados).

## Notas gerais desta sessão

- 4 agentes paralelos em worktrees isolados sofreram interrupções (limite de
  sessão da conta, 2 stalls por watchdog de stream, 1 kill manual do
  utilizador) — nenhuma delas indicou bug no trabalho em si, só instabilidade
  de infraestrutura. Foi preciso retomar cada worktree em vez de recomeçar,
  para não perder trabalho já feito.
- Cada stream evitava tocar nos mesmos ficheiros onde possível, mas
  `JtaAnnotationProcessor.java`, `ComponentMetadata.java` e `pom.xml` raiz
  foram tocados por mais do que um stream — reconciliação manual é esperada,
  não é sinal de erro.
