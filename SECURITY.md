# SECURITY.md — Revisão de segurança

**O que isto é:** auditoria de código-fonte, complementada onde possível
por testes automatizados de ponta a ponta contra a aplicação real
(`mvn verify`, `SecurityRegressionTest`) — não é um pentest dinâmico
completo contra um ambiente de produção. Os achados #1–#8 (rodada
original) foram revisão estática, feita num ambiente sem acesso ao Maven
Central. A partir da revisão de 2026-08-24 (achados #9–#11), o ambiente
já compila e roda `jta-spring-boot-starter`/`jta-demo` de verdade, e os
achados #1/#5 (os dois mais críticos) ganharam teste de regressão HTTP
real em `SecurityRegressionTest` — a ressalva "nada foi confirmado
disparando requisições reais" não vale mais para esses dois.

## Achados corrigidos nesta revisão

### 1. CRÍTICO — Invocação de método arbitrário via `?action=`

**Onde:** `JtaActionController` + `JtaComponentInvoker`.

**O problema:** o processor já valida em compile-time exatamente quais
métodos `void` de um componente são ações legítimas
(`ComponentMetadata.actions()`). Mas o controller nunca consultava essa
lista — `action` (um `@RequestParam` cru, 100% controlado por quem faz a
requisição) ia direto para `invoker.invokeAction(instance, action)`, que
resolvia o método por reflection assim:

```java
for (Method method : type.getMethods()) {
    if (method.getName().equals(name) && method.getParameterCount() == 0) {
        return method;
    }
}
```

Isso casa **qualquer método público sem argumentos** da classe — não só
os `void` declarados como ação. Um atacante que descubra o seletor de um
componente (visível no HTML gerado, ex: `data-jta-component="..."`) podia:

- Invocar métodos de template (que retornam valor e não deveriam ser
  reentrantes via POST, mas ainda assim executam);
- Invocar métodos herdados de `Object` (`wait()`, `notify()`,
  `hashCode()`...) — `wait()` em particular podia travar a thread da
  requisição;
- Em geral, invocar qualquer método público que o dev nunca pretendeu
  expor via HTTP, só porque é público na classe.

**Exploração de exemplo:**
```
POST /__jta/action/dev-jta-demo-produtos-produto-detalhe?action=wait
```

**Correção:** `JtaActionController` agora checa
`metadata.actions().contains(action)` antes de fazer qualquer coisa, e
`JtaComponentInvoker` (defesa em profundidade) só resolve métodos cujo
retorno seja `void` — a mesma definição de "ação" que o processor já usa
em compile-time, agora também aplicada em runtime.

### 2. ALTO — Console do H2 exposto sem autenticação

**Onde:** `jta-demo/src/main/resources/application.properties`.

**O problema:** `spring.h2.console.enabled=true` no profile padrão, sem
nenhum Spring Security configurado no projeto. Isso deixa um console SQL
completo em `/h2-console`, acessível por qualquer um que alcance o
servidor — sem senha, sem login. Versões antigas do H2 tiveram até RCE de
verdade através desse console (manipulação da JDBC URL — ex:
CVE-2021-42392, CVE-2022-23221). Mesmo sem RCE, é leitura/escrita completa
no banco.

**Correção:** removido do profile padrão; movido para
`application-dev.properties`, ativado só com
`--spring.profiles.active=dev` — nunca ligado por padrão.

### 3. MÉDIO — `isAuthenticated()` sozinho não exclui sessão anônima

**Onde:** `JtaSecurityEnforcer`.

**O problema:** o Spring Security representa "ninguém logado" como um
`AnonymousAuthenticationToken` cujo `isAuthenticated()` retorna `true` por
design (é "autenticado como anônimo", não ausência de autenticação). A
checagem original (`authentication == null || !authentication.isAuthenticated()`)
não excluía esse caso. Na prática o loop de match de role seguinte
provavelmente ainda bloquearia (a menos que a role exigida coincidisse com
a authority padrão de usuário anônimo), mas é um erro de padrão conhecido
o suficiente para ter nome na comunidade Spring Security, e o código não
deveria depender de uma coincidência de nomes de role para estar correto.

**Correção:** adicionada a exclusão explícita de
`AnonymousAuthenticationToken`, o padrão correto e documentado do Spring
Security para essa checagem.

### 4. ALTO — `@Sse` nunca verificava `@RequiresRole`

**Onde:** `JtaSseController`.

**O problema:** gap introduzido na própria rodada anterior, ao adicionar
suporte a SSE — os outros dois pontos de entrada (`JtaRouteRegistrar` para
GET de página, `JtaActionController` para ações) checam
`JtaSecurityEnforcer.isAuthorized(metadata)` corretamente, mas
`JtaSseController.connect(...)` nunca fazia essa checagem. Um componente
que combinasse `@Sse` com `@RequiresRole` transmitia o HTML renderizado
para **qualquer um** que conectasse no endpoint SSE, ignorando
completamente a restrição de role.

**Correção:** `connect()` agora resolve o `ComponentMetadata` do path
casado e nega a conexão (`403`) se `JtaSecurityEnforcer.isAuthorized`
retornar falso — mesma checagem, mesmo ponto do ciclo de vida (na conexão,
antes de qualquer emissor ser criado).

### 5. MÉDIO — Mass assignment: qualquer campo público era bindável remotamente

**Onde:** `JtaComponentInvoker.populateFromParams`/`populateFromPathVariables`,
`ComponentMetadata`, `JtaAnnotationProcessor`, `TemplateTransformer`.

**O problema:** as duas funções de bind iteravam **todos** os campos
públicos do componente e populavam qualquer um que batesse com o nome de
um parâmetro da requisição — sem relação nenhuma com o que o template
realmente referencia via `{{ }}`. Um campo público pensado como "só uso
interno" (ex: um `boolean isAdmin`) era igualmente settable via query
string/form data, só por ser público.

**Correção — seguro por padrão, sem quebrar nenhum componente existente:**
o processor agora calcula `ComponentMetadata.bindableFields()` em
compile-time = (campos que o template referencia via `{{ }}`) ∪ (campos
declarados como `{param}` de rota) ∪ (campos anotados explicitamente
`@Bindable`, a válvula de escape para o caso raro de um campo usado só
dentro de uma expressão JTE nativa como `@if`/`@for`, nunca interpolado
diretamente). `populateFromParams`/`populateFromPathVariables` agora só
populam campos dentro desse conjunto — um campo público que o template
nunca menciona simplesmente não é mais alvo de bind.

**Por que isso não quebra a experiência de dev/usuário:** o modelo do JTA
já era "o template reenvia o estado que precisa via `{{ campo }}` em
inputs escondidos/visíveis" — todo componente do demo já tinha 100% dos
campos que precisava bindar literalmente interpolados em algum lugar do
próprio template (verificado campo a campo antes de implementar a
mudança). Nenhum componente existente precisou de `@Bindable` explícito;
a válvula de escape existe para casos futuros, não para consertar os
atuais. Validado em compile-time (ver `scripts/smoke-test.sh`): um campo
`isAdmin` nunca referenciado corretamente fica de fora de
`bindableFields`, enquanto um campo `@Bindable` sem uso no template entra.

## Achados documentados, não corrigidos nesta revisão

Estes são decisões de design com implicações de segurança reais, mas que
exigem mudança de contrato do framework maior do que um bugfix pontual —
por isso documentados com orientação em vez de corrigidos nesta revisão.

### 6. BAIXO — Sem integração de CSRF

O endpoint de ação é `POST` puro, sem nenhum token CSRF emitido ou
verificado pelo JTA. Hoje isso não é explorável no demo (não há
autenticação nenhuma configurada, logo não há sessão para sequestrar via
CSRF). Mas se um consumidor adicionar Spring Security com login baseado em
sessão, o comportamento padrão do Spring Security (CSRF ligado por padrão)
vai **bloquear** as ações do JTA com 403, já que nenhum token é enviado —
falha fechado, não é um buraco de segurança, mas é fricção de integração
que vale documentar: HTMX suporta enviar o token CSRF via `hx-headers`
lendo de uma meta tag; o JTA ainda não injeta isso automaticamente no
`PageShellRenderer`.

### 7. BAIXO — SSE sem limite de conexões concorrentes

`JtaSseController` aceita conexões SSE ilimitadas por path
(`CopyOnWriteArrayList` sem teto). Um cliente (ou vários) abrindo muitas
conexões sem fechar esgota threads/memória do servidor gradualmente. Sem
mitigação nesta versão — um limite de conexões por IP/sessão seria o
próximo passo natural.

### 8. INFORMATIVO — `Redirect(path)` não valida o destino

`throw new Redirect(caminho)` aceita qualquer string. No demo atual todo
uso é com caminho gerado pelo próprio servidor (ex:
`"/produtos/" + produto.getId()`, onde o ID é um UUID gerado
internamente) — sem exploração possível hoje. Mas se um dev um dia fizer
`new Redirect(request.getParameter("returnTo"))`, isso seria um open
redirect clássico. O framework não valida nem restringe o destino a
caminhos relativos/mesma origem — vale considerar essa validação no
construtor de `Redirect` numa fase futura.

## Revisão adicional (2026-08-24) — mapeada ao OWASP Top 10 (2021)

Rodada de revisão independente, desta vez com o projeto compilando de
verdade neste ambiente (`mvn verify`, 20 testes) — ver nota atualizada no
final de "Como isto foi verificado".

### 9. MÉDIO (A06:2021 — Vulnerable and Outdated Components) — `jte` tinha uma CVE de XSS — CORRIGIDO

**Onde:** `jte.version` no `pom.xml` raiz (herdado por todo módulo que
renderiza HTML).

**O problema:** a versão fixada, `3.1.15`, tem uma vulnerabilidade
publicada e revisada pelo GitHub —
[GHSA-vh22-6c6h-rm8q / CVE-2025-23026](https://github.com/casid/jte/security/advisories/GHSA-vh22-6c6h-rm8q)
(CWE-79). O escapador de `<script>` do JTE (`Escape.javaScriptBlock`/
`javaScriptAttribute`) não escapa *backticks* nem `$` — um template com
`<script>let x = \`${self.campo}\`;</script>` permite que um valor
controlado pelo usuário contendo backtick quebre para fora da string e
injete JavaScript arbitrário. Nenhum componente do demo usa esse padrão
hoje (exposição atual é zero), mas o JTE renderiza *toda* página de *todo*
consumidor do framework — é uma vulnerabilidade latente na dependência,
não no código do JTA.

**Correção:** `jte.version` atualizado para `3.1.16` (última patch da
série 3.1.x, é onde a CVE foi corrigida — não precisou saltar para a
série 3.2.x). Atualizado também no scaffold gerado por `jta init`.

### 10. BAIXO (A09:2021 — Security Logging and Monitoring Failures) — negação de autorização/ação não deixava rastro — CORRIGIDO

**Onde:** `JtaSecurityEnforcer.isAuthorized`, `JtaActionController`.

**O problema:** os três pontos que negam acesso (`JtaActionController`,
`JtaRouteRegistrar`, `JtaSseController`) devolviam `403`/`404`
silenciosamente. Uma varredura de `?action=` contra vários nomes de
método (tentando reproduzir o achado #1), ou contra várias roles, não
gerava nenhum log — zero visibilidade operacional de uma tentativa de
exploração ativa.

**Correção:** `JtaSecurityEnforcer.isAuthorized` (checado pelos três
pontos de entrada) agora loga em `WARN` (via SLF4J, integrado ao pipeline
de log real da aplicação) toda negação, com o selector e as roles
exigidas — ambos strings geradas pelo processor em compile-time, nunca
vindas do atacante, então seguras de logar sem sanitização.
`JtaActionController` também loga em `WARN` toda tentativa de invocar uma
`action` fora do allowlist — aqui o valor **é** controlado pelo cliente,
então é sanitizado (remoção de `\r`/`\n`/`\t`, truncado em 100 caracteres)
antes de logar, para não permitir log injection (forjar linhas de log
falsas). De passagem, `JtaExceptionHandler` (adicionado na revisão
anterior) trocou de `java.util.logging` para SLF4J, pela mesma razão:
garantir que os logs realmente apareçam no pipeline configurado da
aplicação (Logback via Spring Boot), não num canal separado que o
consumidor talvez nunca veja.

### 11. BAIXO/INFORMATIVO (A01:2021 — Broken Access Control / IDOR) — sem autorização por objeto — documentado, não corrigido

**Onde:** modelo de segurança do framework como um todo
(`@RequiresRole`/`JtaSecurityEnforcer`).

**O problema:** `@RequiresRole` autoriza por **rota/classe**, não por
**instância**. Em `/produtos/{id}/editar`, por exemplo, qualquer usuário
autorizado a acessar aquela *rota* pode editar *qualquer* `id` — não há
checagem de que o `id` pertence ao usuário autenticado. Isso é a classe
clássica de IDOR (Insecure Direct Object Reference). Hoje isso é
responsabilidade inteira do dev, dentro do corpo da action (ex:
`if (!produto.getDonoId().equals(usuarioAtual())) throw ...`), e o
framework não oferece nenhum hook nem orientação para isso.

**Por que não foi corrigido nesta revisão:** não há uma forma genérica de
"dono do recurso" no modelo atual do JTA (compontentes não têm noção de
usuário/tenant) — resolver isso de verdade exigiria uma extensão de
contrato (ex: uma anotação `@OwnedBy` ou um hook `boolean autorizado()`
convencional chamado antes de renderizar/agir), não um bugfix pontual.
Documentado aqui para o dev que for construir um app com recursos por
usuário saber que precisa adicionar essa checagem manualmente — o JTA não
faz isso por ele.

## Como isto foi verificado

Os achados #1–#4 (jta-spring-boot-starter) são revisão de código, não
testes executados — este ambiente não consegue compilar
`jta-spring-boot-starter` (depende de Spring, fora do acesso ao Maven
Central aqui). O achado #5 é diferente: a parte que decide **quais**
campos entram em `bindableFields` roda inteira em `jta-processor`
(zero dependências), então essa parte foi validada de verdade com
`javac` puro — um campo nunca referenciado (`isAdmin`) fica de fora,
um campo `@Bindable` sem uso no template entra, um path param entra
mesmo sem interpolação. Só a metade que efetivamente *aplica* esse
allowlist em runtime (`JtaComponentInvoker`) não foi compilada aqui.

`jta-core`/`jta-processor` continuam validados por
`scripts/smoke-test.sh` (42 + 7 asserções, 0 falhas — inclui o teste
dedicado de `bindableFields`). Recomendação: depois de `mvn clean verify`,
valide manualmente:
- achado #1: `POST /__jta/action/{qualquer-selector}?action=hashCode` (ou
  qualquer método fora da lista de ações declaradas) deve voltar `404`,
  não `200`.
- achado #5: adicione um campo público não referenciado em nenhum
  template (ex: `public boolean debug = false;`) a um componente
  existente, envie `?debug=true` numa ação, e confirme que o campo
  continua `false` depois — antes da correção, ficaria `true`.
