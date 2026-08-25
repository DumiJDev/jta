/**
 * Utilitarios de teste para aplicacoes construidas sobre o JTA - nao para
 * testar o proprio framework (isso ja e coberto pelos testes internos de
 * cada modulo: {@code jta-core}, {@code jta-runtime}, {@code jta-processor},
 * cada starter).
 *
 * <p><b>Ponto de entrada:</b> {@link dev.jta.test.JtaTestHarness}. Ele monta
 * o mesmo {@code ComponentRegistry}/{@code TemplateEngine}/{@code ComponentInvoker}
 * que um starter real monta (por padrao: registry lido do classpath de
 * teste, {@code TemplateEngine.createPrecompiled}, {@code ReflectionComponentFactory})
 * e delega para {@code JtaPageDispatcher}/{@code JtaActionDispatcher} de
 * {@code jta-runtime} sem reimplementar nenhuma logica de dispatch/seguranca -
 * um teste escrito com este harness exercita exatamente o mesmo caminho de
 * codigo que roda em producao, so trocando o adaptador HTTP por chamada
 * direta.
 *
 * <p>{@link dev.jta.test.JtaAssertions} da atalhos para os tipos selados
 * {@code PageResult}/{@code ActionResult} ({@code assertRendered},
 * {@code assertForbidden}, {@code assertRedirect}, {@code assertNotFound},
 * {@code assertContains}) - lancam {@link java.lang.AssertionError} puro,
 * entao funcionam com qualquer runner (JUnit, TestNG) sem este modulo
 * exigir uma dependencia especifica de asserção.
 *
 * <p>{@link dev.jta.test.TestCurrentUser} constroi um {@code CurrentUser}
 * fake (autenticado com roles, ou anonimo) para exercitar
 * {@code @RequiresRole}/{@code @AllowAnonymous} sem precisar de Spring
 * Security (ou qualquer outro provedor de autenticacao) no classpath de
 * teste.
 *
 * <h2>Fora de escopo deste corte: mock de CSRF e de sessao</h2>
 *
 * <p>Este pacote foi implementado numa branch/worktree isolada que ainda
 * NAO tem os tipos de CSRF nativo (double-submit HMAC) e de sessao
 * agnostica de framework - eles fazem parte de outra fase do plano mestre,
 * desenvolvida em paralelo, e nao existem em {@code jta-runtime} nesta
 * arvore no momento em que este modulo foi escrito. Nao ha nenhum tipo
 * aqui que dependa deles (nada quebraria se essa fase nunca chegasse a
 * esta branch), mas fica documentado abaixo o formato esperado, para quem
 * for adicionar esse suporte depois de fazer merge das duas fases:
 *
 * <ul>
 *   <li>{@code dev.jta.runtime.session.JtaSession} - interface com
 *       {@code id()}, {@code attribute(String)}, {@code setAttribute(String, Object)},
 *       {@code removeAttribute(String)}, {@code invalidate()}, e um factory
 *       estatico {@code JtaSession.none()} para o caso "sem sessao". Um
 *       {@code TestJtaSession} analogo a {@link dev.jta.test.TestCurrentUser}
 *       (um {@code Map} em memoria por tras da interface) seria o
 *       complemento natural aqui.</li>
 *   <li>{@code dev.jta.runtime.csrf.CsrfTokenStore} - gera/verifica o par
 *       cookie+header do double-submit; {@code CsrfRequest(cookieHeader, headerValue)}
 *       e o tipo usado para representar a requisicao verificada. Um harness
 *       de teste completo precisaria de um jeito de obter um par
 *       cookie/header valido (via um {@code CsrfTokenStore} de teste, ex:
 *       {@code NoopCsrfTokenStore} ja usado quando {@code csrf_mode = "disabled"}
 *       em {@code jta.config.toml}) para popular {@code JtaActionDispatcher}
 *       sem precisar de um roundtrip HTTP real.</li>
 * </ul>
 *
 * <p>Quando esses tipos existirem nesta branch, o jeito natural de os
 * expor aqui e um {@code JtaTestHarness.withSession(JtaSession)} /
 * {@code withCsrfToken(CsrfToken)} opcionais, com defaults que preservam o
 * comportamento atual (sem sessao, CSRF desabilitado) - nenhuma mudanca
 * de assinatura das APIs publicas ja existentes deste modulo seria
 * necessaria.
 */
package dev.jta.test;
