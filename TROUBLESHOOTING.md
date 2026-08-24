# Troubleshooting — bugs reais encontrados na implementação inicial

Este arquivo documenta os problemas reais encontrados rodando o projeto pela
primeira vez fora do ambiente de desenvolvimento original, com sintoma, causa
e correção. Existe para que quem clonar isso depois (inclusive você, daqui a
alguns meses) não perca tempo redescobrindo o mesmo. Ver também
`ContadorIntegrationTest` — cada um destes bugs tem uma asserção correspondente
que teria pego o problema automaticamente via `mvn verify`.

## 1. `no main manifest attribute` ao rodar `java -jar jta-demo.jar`

**Causa:** `spring-boot-maven-plugin` estava declarado no `pom.xml` mas sem
`<executions>` vinculando o goal `repackage`. Diferente de plugins como o
compilador, o Spring Boot plugin não roda sozinho só por estar declarado
quando `packaging` é `jar` comum — produz o jar fino padrão, sem o manifest
com `Main-Class`.

**Correção:** vincular explicitamente o goal:
```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>repackage</goal></goals>
        </execution>
    </executions>
</plugin>
```

## 2. `@param dev.jta.demo.Contador this` — erro de compilação do JTE

**Sintoma:** `'this' is allowed as the parameter name for the receiver type only`.

**Causa:** `this` é palavra reservada em Java; só é válida como o parâmetro
receiver especial (primeiro parâmetro, sintaxe própria), não como um `@param`
comum do JTE — que gera código Java literal usando esse nome.

**Correção:** `JtaAnnotationProcessor` usa `self` em vez de `this` no `@param`
gerado, e `TemplateTransformer` gera `${self.campo}` em vez de `${this.campo}`.

## 3. `package dev.jta.demo does not exist` em runtime, no meio de HTML gerado

**Sintoma:** erro de compilação do JTE acontecendo em **runtime**, com o
caminho de arquivo apontando para `jte-classes\...\ondemand\...`.

**Causa:** `JtaAutoConfiguration` criava o `TemplateEngine` em modo **on-demand**
(`TemplateEngine.create(resolver, ...)`), que recompila o `.jte` a partir do
source a cada resolução, usando um classpath isolado que não enxerga as
classes da própria aplicação. Isso é incompatível com o `jte-maven-plugin`
(goal `precompile`), que já compilou os templates em bytecode durante o build.

**Correção:** usar o modo pré-compilado, que carrega as classes já compiladas
via classloader normal:
```java
TemplateEngine.createPrecompiled(ContentType.Html)
```

## 4. Cliques no demo não funcionavam

**Causa:** nenhuma página gerada importava o HTMX — o fragmento HTML do
componente era devolvido cru, sem `<html>`/`<head>`/`<script>`. Sem a
biblioteca carregada no browser, os atributos `hx-*` são só atributos HTML
inertes.

**Correção:** `PageShellRenderer` envolve toda resposta de página (`@Route`,
via `JtaRouteRegistrar`) num documento HTML completo com
`<script src=".../htmx.org...">`. Fragmentos de ação (`JtaActionController`)
continuam sendo devolvidos crus — é o que o HTMX espera para fazer o swap.

## 5. CSS do componente não era aplicado

**Causa:** o conteúdo de `@AComponent(style=...)` nunca era processado nem
emitido em lugar nenhum — só o atributo `data-jta-component` na raiz existia,
mas o CSS que deveria usá-lo como escopo nunca chegava ao browser.

**Correção:** `CssScoper` (jta-processor) prefixa cada regra top-level do
`style()` com `[data-jta-component="<selector>"]` em compile-time; o resultado
vai para `ComponentMetadata.scopedCss()`, agregado por `PageShellRenderer` num
único `<style>` na página.

## 6. `parameter name information not available via reflection`

**Sintoma:** `IllegalArgumentException` ao acessar `/__jta/action/{selector}`.

**Causa:** `@PathVariable String selector` sem nome explícito depende da flag
`-parameters` do compilador para recuperar o nome do parâmetro via reflection
em runtime — o Maven não liga essa flag por padrão, então o bytecode só tinha
`arg0`, `arg1`, etc.

**Correção:** nome explícito (`@PathVariable("selector")`) + flag
`<parameters>true</parameters>` ligada globalmente no `maven-compiler-plugin`
do parent `pom.xml`, para não repetir o mesmo bug em bindings futuros.

## 7. `Missing @endfor` — mas o template não tem nenhum `@for`

**Sintoma:** erro de compilação do JTE apontando para um `.jte` gerado,
reclamando de uma diretiva `@for`/`@if` sem fechamento, mesmo quando o
componente não usa essas diretivas.

**Causa:** o JTE trata **qualquer** `@` no HTML gerado como início de uma
diretiva — inclusive dentro de texto comum. Um link de navegação com o texto
"DI de um `@Service` Spring" ou um e-mail escrito à mão ("contato@exemplo.com")
já é suficiente: o JTE vê `@Service`/`@exemplo` e tenta interpretar como
início de diretiva, esperando um fechamento que nunca vem.

**Correção definitiva:** reescrever o texto sem o caractere `@`, ou envolver
o trecho com `@raw ... @endraw` (a forma oficial do JTE de desligar o
processamento de diretivas numa região — não existe um escape de caractere
único como `@@`).

**Correção estrutural:** `TemplateTransformer` agora detecta qualquer `@`
que não seja parte de uma diretiva JTE reconhecida (`@if`, `@for`, `@raw`,
etc.) e falha o build em **compile-time**, com uma mensagem que aponta o
trecho exato — em vez de deixar o JTE falhar em runtime com um erro
apontando para código gerado que o dev nunca escreveu.

## 8. Processor trava com `AnnotationTypeMismatchException` em vez de erro limpo

**Sintoma:** `An annotation processor threw an uncaught exception` seguido de
`AnnotationTypeMismatchException: Incorrectly typed data found for annotation
element ... template() (Found data of type none)`.

**Causa:** quando outro erro de compilação existe no mesmo módulo e impede uma
expressão constante usada em `@AComponent(template = "..." + Algo.CONSTANTE)`
de ser resolvida (ex: `Algo` não existe, ou tem um erro de compilação
próprio), o valor da anotação chega ao processor "quebrado" - chamar
`annotation.template()` nessa condição lança uma exceção de runtime não
tratada, travando o build inteiro com uma mensagem genérica em vez de
apontar para o erro real (que normalmente já apareceu ANTES no output do
`javac`, só ofuscado pelo crash do processor logo em seguida).

**Correção:** `JtaAnnotationProcessor.process()` agora captura
`AnnotationTypeMismatchException`/`IncompleteAnnotationException` ao redor de
cada componente processado, reportando um erro claro que orienta a procurar
erros de compilação anteriores no output - em vez de deixar o processor
travar sem tratamento. Validado simulando o cenário exato (símbolo não
resolvido dentro da expressão de `template()`).

## 9. Tag raiz com hífen (`<router-outlet/>`) tinha o nome corrompido

**Sintoma:** ao implementar `@Layout`/`<router-outlet/>` e testar um layout onde
o outlet é a própria tag raiz, o atributo `data-jta-component` era inserido
**no meio do nome da tag**: `<router-outlet/>` virava
`<router data-jta-component="...".-outlet/>`.

**Causa:** a regex que localiza a primeira tag do template para injetar o
atributo de escopo (`FIRST_OPEN_TAG`) só aceitava `[a-zA-Z][a-zA-Z0-9]*` no
nome da tag - sem hífen. Qualquer tag com hífen como raiz (custom elements,
web components, o próprio `router-outlet`) tinha o nome cortado no primeiro
hífen, e o atributo era injetado bem ali no meio.

**Correção:** `FIRST_OPEN_TAG` passou a aceitar `[a-zA-Z][a-zA-Z0-9-]*`.
Encontrado e corrigido durante a própria validação do recurso de layout -
o teste de "mais de um `<router-outlet/>`" só passou depois da correção,
porque antes o primeiro outlet ficava corrompido e "desaparecia" da
contagem, mascarando o erro que o teste deveria capturar.

---

## 10. `connect(String path)` num handler HTTP não tem quem resolva o parâmetro

**Sintoma (previsto, pego na própria revisão antes de chegar a você):** ao
implementar `@Sse`, o primeiro rascunho de `JtaSseController.connect`
recebia um `String path` puro como parâmetro do handler registrado via
`RequestMappingHandlerMapping.registerMapping`. Isso quebraria em runtime -
diferente de `HttpServletRequest` (um tipo especial que o Spring MVC
sempre sabe resolver), um `String` sem anotação (`@PathVariable`,
`@RequestParam`) não tem nenhum `HandlerMethodArgumentResolver` que saiba
de onde tirar o valor.

**Causa:** confundir "parâmetro de método Java" com "algo que o Spring MVC
sabe injetar automaticamente" - só um conjunto específico de tipos
(`HttpServletRequest`, `HttpServletResponse`, tipos anotados) são
resolvidos; qualquer outro tipo precisa de anotação explícita ou não
funciona.

**Correção:** seguir o mesmo padrão já usado (e já validado) em
`JtaRouteRegistrar`/`JtaActionController`: receber `HttpServletRequest` e
ler `HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE` manualmente, em vez
de tentar receber o path já resolvido como parâmetro.

---

**Padrão geral por trás dos 10:** a maioria (1-6, 8) foi erro de **fiação entre
ferramentas** (Maven ↔ Spring Boot, JTE build-time ↔ runtime, HTML gerado ↔
HTMX no browser) que só apareciam rodando de verdade — não erros na lógica
central (derivação de seletor, transformação de template, validação de
bindings), que tinha testes desde o início. O bug #7 é diferente: é
exatamente essa lógica central pegando um problema de integração (texto
comum colidindo com a sintaxe do JTE) *antes* dele virar um erro confuso em
runtime — o padrão que os outros seis não tinham e que agora existe para
prevenir uma nova categoria inteira de bug, não só corrigir um caso
específico. É o que `ContadorIntegrationTest` (que sobe a aplicação real) e
o CI (`.github/workflows/build.yml`) existem para reforçar daqui pra frente.
