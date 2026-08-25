package dev.jta.runtime;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Gera um token unico por render (nao por compilacao, nao por classe -
 * por CHAMADA de render), usado para escopar {@code hx-include} de um
 * componente as suas proprias fronteiras quando ele tem filhos aninhados.
 *
 * <p><b>Por que isto existe:</b> {@code hx-include="closest [data-jta-component]"}
 * (o mecanismo usado antes de aninhamento existir) resolve, via
 * {@code Element.closest(...)}, um UNICO container ancestral relativo ao
 * elemento que disparou o evento - correto e escopado por instancia. Mas
 * o htmx (verificado contra o build real 4.0.0-beta6 pinado neste
 * projeto, ver {@code PageShellRenderer.DEFAULT_HTMX_CDN_URL}) usa, por
 * baixo, uma selecao de campos de formulario HARDCODED
 * ({@code elt.querySelectorAll('[name]:not(button)')}) sobre o container
 * resolvido - essa selecao NAO pode ser customizada via a string do
 * atributo {@code hx-include} (nao ha como injetar um {@code :not(...)}
 * de exclusao dentro dela). E {@code closest(X)} tambem nao serve pra
 * expressar "descendente de X excluindo sub-fronteiras", porque
 * {@code closest} testa se o proprio ancestral bate com X inteiro (nao
 * faz busca de descendente).
 *
 * <p>A alternativa real que funciona (confirmada empiricamente lendo
 * {@code #findAllExt}/{@code #addInputValues} no bundle do htmx 4.0.0-beta6):
 * um SELETOR CSS puro (sem prefixo "closest "/"find ") e avaliado via
 * {@code document.querySelectorAll(...)} de verdade, suportando
 * {@code :is()}/{@code :not()} com selecionadores compostos (CSS
 * Selectors Level 4) - mas roda GLOBAL (documento inteiro), nao relativo
 * ao elemento clicado. Para nao vazar entre instancias irmãs do MESMO
 * componente (ex: o mesmo componente pai repetido dentro de um
 * {@code @for}), cada render precisa de um identificador proprio, unico
 * na pagina, para ancorar o seletor global especificamente aquela
 * instancia - e isso que este contador fornece.
 *
 * <p>Cada {@code .jte} gerado declara uma variavel local
 * {@code __jtaScope} (via {@code !{...}}) chamando {@link #next()} UMA
 * VEZ por render, usada tanto no atributo {@code data-jta-scope} da raiz
 * do template quanto em qualquer {@code hx-include} gerado dentro do
 * mesmo render - inclusive repetido a cada iteracao de um {@code @for},
 * ja que a chamada a {@code @template.X(...)} acontece de novo a cada
 * volta do loop.
 */
public final class RenderScope {

    private static final AtomicLong COUNTER = new AtomicLong();

    private RenderScope() {
    }

    public static long next() {
        return COUNTER.incrementAndGet();
    }
}
