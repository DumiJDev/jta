# Estado da execução do plano-mestre JTA

## 2026-08-26 — Reconciliação concluída, fases 3/4/5 em master

Os 4 streams descritos na secção "sessão 2026-08-25" (abaixo) foram todos
terminados, commitados e reconciliados em `master`. `master` está em `1fc5c02`,
pushed para `origin`, build completo (`mvn clean install`, reactor inteiro)
verde. Os worktrees e branches de cada stream foram removidos (já estão
integrados via os merges abaixo, não há mais nada a recuperar deles).

Cadeia de merges (`git log --graph` a partir de `d2f2f04`):
```
1fc5c02 Reconcilia fase 5a (SSE + TCK) com fases 3/4/5b e master
c3dab36 Reconcilia fase 5b (ferramentas) com fases 3/4 e master
7c792d8 Reconcilia fase 3 (correcao de dados) com fase 4 e master
7bfe39c Reconcilia fase 4 restante (slots, flash, upload, paginas de erro) com master
d2f2f04 Remove jta-demo (gestão escolar) para reconstruir o exemplo do zero
```

Com isto, **as fases 0-5 do plano-mestre estão todas fechadas em master**,
com as ressalvas/gaps documentados abaixo (nada é silencioso — cada um está
declarado no código, no README ou na matriz de compatibilidade do TCK).

### Bug real encontrado durante a reconciliação (já corrigido)

`SseHub.broadcast` (`jta-runtime/src/main/java/dev/jta/runtime/SseHub.java`)
usava a assinatura antiga de `render(name, instance, output)` do jte. Assim
que a composição de componentes (`@Use`/`@Input`) aterrou, os templates
gerados passaram a exigir um segundo parâmetro (`__jtaInvoker`), e todo
`broadcast` começou a rebentar com `IllegalArgumentException: wrong number
of arguments` — engolido silenciosamente pelo catch como WARN. Resultado:
**todo endpoint `@Sse`, nos 4 adapters, estava mudo desde que a composição
foi introduzida.** Corrigido para a forma de `Map` (`self` + `__jtaInvoker`),
igual ao `JtaPageDispatcher`/`JtaErrorPageRenderer`. Só foi detetado porque
o passo final de verificação correu `mvn clean` antes de testar — um
`target/` com o template antigo compilado escondia o bug.

### Gaps conhecidos, documentados (não são silenciosos)

- **i18n por-requisição**: só o Spring adapter resolve `Accept-Language` via
  `LocaleResolver`. Javalin/standalone/Quarkus continuam em
  `Locale.getDefault()` — sem regressão, mas sem locale por-requisição.
- **Upload + error-pages-como-componente**: só Spring e standalone têm
  wiring completo. Javalin e Quarkus compilam (overload preservado) mas sem
  a feature.
- **Slots**: só slot default (sem nome) é suportado — sem slots nomeados.
- **i18n no Quarkus** (feature diferente da acima): a TCK marca isto como
  gap explícito — `Translations` resolve `ResourceBundle` pelo classloader
  errado sob o `QuarkusClassLoader`.
- **`jta-gradle-plugin`**: aplica `gg.jte.gradle` e propaga
  `-Ajta.resourcesDir`, mas o `JtaAnnotationProcessor` ainda não lê essa
  flag — o plugin existe, mas o dev-loop Gradle não está fechado ponta-a-ponta.
- **Maven Central**: config pronta (`io.github.dumijdev`, licenses, scm,
  gpg/javadoc/sources plugins, `central-publishing-maven-plugin`) mas
  **nenhum deploy real foi feito** — falta decisão + execução do utilizador.
- **`jta-test`**: CSRF/sessão ainda não são mockáveis no harness (documentado
  em `package-info.java`).
- **`scripts/check-compat-matrix.sh`** (CI gate comparando a matriz TCK
  gerada com o README): referenciado mas não criado — o check funciona
  manualmente (`CompatibilityMatrixGenerator --check`), só não está no CI.

## Próximo passo: reconstruir o jta-demo

`jta-demo` (gestão escolar) foi removido deliberadamente em `d2f2f04` — o
utilizador não queria mais aquele domínio e pediu um exemplo pensado para
atrair contribuidores, com bloco técnico intencional: forçar o uso orgânico
de SSE, upload, composição/slots, CSRF e i18n (decisão tomada via
AskUserQuestion na sessão 2026-08-25/26, opção "Showcase técnico deliberado").

Isto **agora pode ser feito contra o master atual** (antes não fazia
sentido — as features que o demo deveria mostrar só existiam nos branches
não reconciliados). Nenhum domínio/nome foi decidido ainda.

Sugestão levantada mas não confirmada com o utilizador: um mini quadro
Kanban colaborativo em tempo real (tipo Trello) — mapeia bem para todas as
features:
- Composição/slots: Board → List → Card, com slot para badges/anexos no Card.
- SSE: outros utilizadores veem mudanças ao vivo no mesmo board.
- Upload: anexos em cards, avatar de utilizador.
- CSRF: todos os forms de mutação.
- Converters (fase 3): data de vencimento (LocalDate), prioridade (enum),
  responsável (UUID), labels (multi-valor/List<String>).
- i18n: interface PT/EN via Accept-Language.
- Flash + error pages: confirmações de ação, board não encontrado/sem acesso.
- Sessão/roles: dono vs membro do board.

Antes de construir: confirmar domínio/nome com o utilizador (não assumir o
Kanban só porque foi a primeira ideia proposta).

## Notas operacionais para a próxima sessão

- Vários agentes paralelos nesta sessão sofreram interrupções (limite de
  sessão da conta, stalls de watchdog de stream, 1 kill manual do
  utilizador). Nenhuma indicou bug no trabalho em si — só instabilidade de
  infraestrutura. Em todos os casos o caminho certo foi **retomar o mesmo
  worktree/branch** (nunca recriar do zero), porque o trabalho uncommitted
  ficava lá à espera.
- Evitar builds Maven longos via `run_in_background` + polling/Monitor — foi
  a causa direta de pelo menos 2 stalls nesta sessão. Preferir foreground
  com timeout generoso (10-15 min para o reactor inteiro).
- Quando múltiplos streams tocam os mesmos ficheiros-quente
  (`JtaAnnotationProcessor.java`, `ComponentInvoker.java`,
  `JtaActionDispatcher`/`JtaPageDispatcher`, `JtaAutoConfiguration.java`,
  `pom.xml` raiz), a reconciliação manual é esperada — não é sinal de erro,
  e resolver bem significa combinar a lógica dos dois lados, não escolher um.

---

## Sessão 2026-08-25 (histórico, já processado — ver secção acima)

Contexto: plano-mestre tem 6 fases (0-5). Antes desta sessão já estavam
commitados em `master`: fases 0, 1, 2, e a maior parte da fase 4
(composição @Use/@Input, hx-include, deteção de ciclos, argumentos em ações).

Auditoria desta sessão confirmou o que faltava e lançou 4 streams paralelos
(cada um em worktree isolado) para fechar o resto:

1. **Fase 3 — Correção de dados**: `ConverterRegistry` + `ConversionException`
   (enum, LocalDate/LocalDateTime, BigDecimal, UUID, List/array, Optional);
   erro em compile-time para campos bindáveis sem conversor; erros de
   conversão viram erros de formulário; multi-valor em `List<T>`/array;
   `LocaleContext`/`LocaleResolver`/`AcceptLanguageLocaleResolver` ligado ao
   Spring adapter.
2. **Fase 4 (resto)**: slots (`<slot/>`/`<slot>fallback</slot>`, só default);
   `ComponentMetadata` com `hasSlot`/`isErrorPage`/`errorPageStatus`/
   `uploadFields`; `Redirect.withFlashSuccess/withFlashError` +
   `FlashSupport` protegido por `ReservedFieldNames`; `MultipartParser`
   próprio isolado em `jta-standalone`; `JtaErrorPageRenderer` +
   `JtaErrorController` (Spring) + wiring em standalone.
3. **Fase 5b — Ferramentas**: módulo `jta-test`; dev-loop via
   `JtaTemplateEngineFactory`/`DirectoryCodeResolver`; `jta-gradle-plugin`;
   config de publicação Maven Central (`io.github.dumijdev`, Central
   Publishing Portal).
4. **Fase 5a — SSE + TCK**: `SseHub` extraído para `jta-runtime`, wired nos
   4 adapters; módulo `jta-tck` com matriz de compatibilidade gerada,
   checked into README entre `JTA-TCK-MATRIX-BEGIN/END`.

Todos os 4 foram concluídos, commitados e reconciliados em `master` — ver
secção "2026-08-26" acima para o resultado final e os gaps que ficaram.
