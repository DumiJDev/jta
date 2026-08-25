package dev.jta.runtime;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import dev.jta.runtime.csrf.CsrfToken;

/**
 * Monta o documento HTML completo para uma requisicao de pagina
 * (componente com {@code @Route}): importa o HTMX e injeta o CSS
 * escopado de todos os componentes conhecidos num {@code <style>} unico.
 *
 * <p>Sem isso, o fragmento HTML de um componente sozinho nunca teria o
 * HTMX carregado no browser - os atributos {@code hx-*} ficam inertes
 * (nao ha nada escutando o clique) e nenhum CSS aparece, porque o
 * conteudo de {@code @AComponent(style=...)} nunca chega ao browser em
 * lugar nenhum.
 *
 * <p><b>Navegação sem reload de página inteira:</b> {@code <body hx-boost="true">}
 * faz o HTMX interceptar todo {@code <a>}/{@code <form>} sem atributos
 * {@code hx-*} próprios e transformá-los em requisições AJAX progressivas
 * (com {@code HX-Request} no header, histórico via {@code pushState}) -
 * sem exigir nenhum {@code hx-get}/{@code hx-target} manual em cada link.
 * Não conflita com os botões de ação que já declaram {@code hx-post}
 * explicitamente (boost só se aplica a elementos sem atributos {@code hx-*}
 * próprios). Como o CSS agregado aqui já inclui todos os componentes
 * registrados (não só os da página atual), a primeira carga já contém
 * tudo que qualquer navegação boosted subsequente vai precisar - o HTMX
 * ignora o {@code <head>} das respostas seguintes ao fazer boost, então
 * isso funciona sem nenhuma lógica adicional.
 *
 * <p><b>Limitacao conhecida do MVP:</b> o CSS de <em>todos</em> os
 * componentes do classpath e injetado em toda pagina, nao so o dos
 * componentes efetivamente usados nela - simples e correto para uma
 * aplicacao pequena, mas nao escala para dezenas de componentes sem um
 * passo de bundling/tree-shaking (fora do escopo deste corte). Nunca
 * gera CSS incorreto, so mais bytes do que o estritamente necessario.
 *
 * <p>A URL do HTMX vem de {@code [htmx] cdn_url} em {@code jta.config.toml}
 * (ver {@link JtaConfig}), com {@link #DEFAULT_HTMX_CDN_URL} como default
 * quando o arquivo nao existe ou a chave nao esta presente.
 *
 * <p><b>Feature flag do TailwindCSS:</b> {@code [features] tailwindcss = true}
 * em {@code jta.config.toml} troca o CSS de base embutido pelo Tailwind
 * (via CDN "Play", sem passo de build - adequado para prototipagem, nao
 * para producao com purge de classes). As duas coisas juntas
 * conflitariam (o reset/tipografia do {@link #BASE_CSS} brigaria com as
 * classes utilitarias do Tailwind), entao sao mutuamente exclusivas: com
 * a flag ligada, {@link #BASE_CSS} nao e injetado. O CSS escopado por
 * componente ({@code style()}/{@code styleUrl()}) continua sendo injetado
 * de qualquer forma nos dois casos - continua util para overrides pontuais.
 *
 * <p>Movido de {@code jta-spring-boot-starter} (pacote {@code dev.jta.spring})
 * para {@code jta-runtime} na extracao do nucleo agnostico - esta classe
 * ja nao dependia de nada especifico do Spring, so mudou de endereco.
 */
final class PageShellRenderer {

    /**
     * <b>ATENCAO - pino de pre-release:</b> htmx 4.0.0 ainda NAO tem release
     * estavel na data desta mudanca (a serie foi de alpha1 a alpha8, agora
     * em beta1..beta6, sem data de GA anunciada, com breaking changes reais
     * acontecendo entre betas - ex: o evento {@code htmx:swap:finally} foi
     * renomeado para {@code htmx:finally:swap} entre beta5 e beta6). Fixar
     * isto como default de uma biblioteca e uma decisao de risco aceita
     * conscientemente (nao um descuido) - todo consumidor do starter, e todo
     * projeto novo gerado por {@code jta init}, herda uma dependencia de
     * front-end em pre-release. Se um beta futuro quebrar algo, o dev pode
     * voltar para uma versao estavel sobrescrevendo {@code [htmx] cdn_url}
     * em {@code jta.config.toml} (nesse caso o atributo {@code integrity}
     * e omitido automaticamente - ver {@link #wrap} - porque nao ha como
     * cravar o hash de uma URL arbitraria de antemao). Revisitar este pino
     * assim que 4.0.0 sair do beta.
     */
    static final String DEFAULT_HTMX_CDN_URL = "https://unpkg.com/htmx.org@4.0.0-beta6";
    static final String TAILWIND_CDN_URL = "https://cdn.tailwindcss.com";

    /**
     * Subresource Integrity (SHA-384) do arquivo servido em
     * {@link #DEFAULT_HTMX_CDN_URL} - calculado contra o conteudo real
     * publicado pelo unpkg para htmx.org 4.0.0-beta6. Protege contra um
     * comprometimento do CDN (ou de um mirror/MITM) injetando JS diferente
     * do esperado: o browser recusa executar o script se os bytes nao
     * baterem com este hash. So se aplica ao default - se o dev sobrescrever
     * {@code [htmx] cdn_url} em {@code jta.config.toml} (outra versao, outro
     * host), nao ha como saber o hash correto de antemao, entao o atributo
     * e omitido nesse caso (ver {@link #wrap}). Precisa ser recalculado a
     * cada vez que {@link #DEFAULT_HTMX_CDN_URL} mudar de versao.
     */
    static final String DEFAULT_HTMX_INTEGRITY =
            "sha384-6lyVbhrs13b9z7mLOpt/N6R76rtkEBWgCjAXRs/DSWyi2AMnQSs10ijWk+PI8n7W";

    /**
     * CSS de base generico - nao especifico de nenhuma rota da aplicacao
     * (isso ficaria a cargo do dev/demo). Da uma aparencia consistente
     * (tipografia, espacamento, botoes, formularios, cartoes, barra de
     * navegacao) para qualquer app JTA sem exigir que o dev escreva CSS
     * do zero. Vem ANTES do CSS agregado dos componentes, entao
     * @AComponent(style=...) sempre pode sobrescrever quando precisar.
     */
    private static final String BASE_CSS = """
            :root {
              --jta-primary: #2563eb;
              --jta-primary-dark: #1d4ed8;
              --jta-text: #1f2937;
              --jta-muted: #6b7280;
              --jta-bg: #f9fafb;
              --jta-border: #e5e7eb;
              --jta-error: #dc2626;
              --jta-success: #16a34a;
            }
            * { box-sizing: border-box; }
            body {
              margin: 0;
              font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
              background: var(--jta-bg);
              color: var(--jta-text);
            }
            .jta-nav {
              display: flex;
              align-items: center;
              gap: 1.25rem;
              padding: 0.9rem 1.5rem;
              background: white;
              border-bottom: 1px solid var(--jta-border);
            }
            .jta-nav a { color: var(--jta-muted); text-decoration: none; font-size: 0.95rem; }
            .jta-nav a:hover { color: var(--jta-primary); }
            .jta-nav .jta-brand { color: var(--jta-text); font-weight: 600; margin-right: auto; }
            .jta-container { max-width: 720px; margin: 2rem auto; padding: 0 1.5rem 3rem; }
            .jta-container h1 { font-size: 1.5rem; margin-bottom: 1rem; }
            .jta-container a { color: var(--jta-primary); }
            button {
              background: var(--jta-primary);
              color: white;
              border: none;
              padding: 0.55rem 1.1rem;
              border-radius: 6px;
              font-size: 0.95rem;
              cursor: pointer;
            }
            button:hover { background: var(--jta-primary-dark); }
            input { padding: 0.5rem; border: 1px solid var(--jta-border); border-radius: 6px; font-size: 0.95rem; }
            .jta-error { color: var(--jta-error); font-size: 0.875rem; margin: 0.25rem 0 0.75rem; min-height: 1.1em; }
            .jta-success { color: var(--jta-success); }
            .jta-card {
              background: white;
              border: 1px solid var(--jta-border);
              border-radius: 8px;
              padding: 1rem 1.25rem;
              margin-bottom: 0.75rem;
            }
            .jta-field { margin-bottom: 0.5rem; }
            .jta-field label { display: block; font-size: 0.875rem; color: var(--jta-muted); margin-bottom: 0.25rem; }
            .jta-container h2 { font-size: 1.1rem; margin: 1.5rem 0 0.75rem; }
            .jta-table {
              width: 100%;
              border-collapse: collapse;
              margin-bottom: 1rem;
              background: white;
              border: 1px solid var(--jta-border);
              border-radius: 8px;
              overflow: hidden;
            }
            .jta-table th, .jta-table td { text-align: left; padding: 0.6rem 0.9rem; border-bottom: 1px solid var(--jta-border); }
            .jta-table th { font-size: 0.75rem; text-transform: uppercase; letter-spacing: 0.03em; color: var(--jta-muted); background: var(--jta-bg); }
            .jta-table tr:last-child td { border-bottom: none; }
            .jta-table tbody tr:hover td { background: #f3f4f6; }
            .jta-btn-secondary {
              background: white;
              color: var(--jta-text);
              border: 1px solid var(--jta-border);
            }
            .jta-btn-secondary:hover { background: var(--jta-bg); }
            .jta-btn-danger {
              background: white;
              color: var(--jta-error);
              border: 1px solid var(--jta-error);
            }
            .jta-btn-danger:hover { background: var(--jta-error); color: white; }
            """;

    private PageShellRenderer() {
    }

    static String wrap(String bodyHtml, ComponentRegistry registry, JtaConfig config, CsrfToken csrfToken) {
        boolean tailwind = config.getBoolean("features", "tailwindcss", false);

        StringBuilder css = new StringBuilder();
        if (!tailwind) {
            css.append(BASE_CSS).append('\n');
        }
        for (ComponentMetadata metadata : registry.all()) {
            if (metadata.hasStyle()) {
                css.append(metadata.scopedCss()).append('\n');
            }
        }

        String htmxUrl = config.getString("htmx", "cdn_url", DEFAULT_HTMX_CDN_URL);
        String htmxIntegrityAttrs = htmxUrl.equals(DEFAULT_HTMX_CDN_URL)
                ? " integrity=\"" + DEFAULT_HTMX_INTEGRITY + "\" crossorigin=\"anonymous\""
                : "";
        // Tailwind Play CDN nao e versionado por URL (serve sempre a build
        // mais recente), entao um hash SRI fixo quebraria assim que a
        // Tailwind atualizasse o arquivo do lado deles - sem SRI aqui por
        // esse motivo, nao por descuido (ver limitacao ja documentada:
        // "adequado para prototipagem, nao para producao").
        String tailwindScriptTag = tailwind ? "  <script src=\"" + TAILWIND_CDN_URL + "\"></script>\n" : "";

        // Sacada que dispensa JS escrito pelo dev (ver SECURITY.md, achado
        // #6): o servidor ja sabe o valor do token no momento de renderizar
        // a pagina, entao escreve-o literalmente aqui - o HTMX propaga
        // hx-headers a todo pedido disparado por um elemento descendente do
        // <body> (procura o atributo subindo a arvore), cobrindo
        // automaticamente todo hx-post gerado pelo TemplateTransformer sem
        // nenhuma leitura de cookie no browser. Ausente em modo
        // csrf_mode=disabled (csrfToken == null nesse caso).
        String hxHeadersAttr = csrfToken == null
                ? ""
                : " hx-headers='{\"" + escapeForHtmlAttribute(csrfToken.headerName()) + "\":\""
                        + escapeForHtmlAttribute(csrfToken.value()) + "\"}'";

        return "<!DOCTYPE html>\n"
                + "<html lang=\"pt\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "  <script src=\"" + htmxUrl + "\"" + htmxIntegrityAttrs + "></script>\n"
                + tailwindScriptTag
                + "  <style>\n" + css + "  </style>\n"
                + "</head>\n"
                + "<body hx-boost=\"true\"" + hxHeadersAttr + ">\n"
                + bodyHtml + "\n"
                + "</body>\n"
                + "</html>\n";
    }

    /**
     * Escaping minimo para embutir um valor dentro de um atributo HTML
     * delimitado por aspas simples (o {@code hx-headers} acima usa aspas
     * simples porque o JSON dentro dele ja usa aspas duplas). O valor do
     * token e sempre base64url gerado pelo proprio {@code HmacCsrfTokenStore}
     * (alfabeto seguro, sem aspas/barras), mas {@code headerName} vem de
     * config editavel pelo dev - escapado por defesa em profundidade.
     */
    private static String escapeForHtmlAttribute(String value) {
        return value.replace("&", "&amp;")
                .replace("'", "&#39;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
