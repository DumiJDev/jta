package dev.jta.runtime;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import gg.jte.TemplateEngine;
import gg.jte.output.StringOutput;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Renderiza, se registrado, o componente {@code @ErrorPage} de um status
 * HTTP - agnostico de framework web, mesmo espirito de
 * {@link JtaPageDispatcher} (que nao cobre este caso: um erro de status
 * nao tem {@code @Route} nem passou pela autorizacao/reidratacao normal de
 * uma pagina).
 *
 * <p>Cada adaptador decide QUANDO chamar isto (ex: {@code JtaExceptionHandler}/
 * um {@code ErrorController} no Spring) - esta classe so cuida do "COMO
 * renderizar", reaproveitando {@link PageShellRenderer} (mesmo documento
 * HTML completo, CSS agregado, HTMX) e a composicao de {@code @Layout}
 * quando o componente de erro declarar um.
 *
 * <p><b>Sem token CSRF:</b> paginas de erro sao tipicamente terminais (sem
 * formulario de acao critico) e podem ser renderizadas fora do fluxo
 * normal de requisicao (ex: de dentro de um exception handler, sem cookie
 * de requisicao original em maos de forma confiavel) - {@code hx-headers}
 * simplesmente nao e emitido no {@code <body>}, igual ao comportamento de
 * {@code csrf_mode=disabled}.
 */
public final class JtaErrorPageRenderer {

    private final ComponentRegistry registry;
    private final ComponentInvoker invoker;
    private final TemplateEngine templateEngine;
    private final JtaConfig config;

    public JtaErrorPageRenderer(ComponentRegistry registry, ComponentInvoker invoker, TemplateEngine templateEngine, JtaConfig config) {
        this.registry = registry;
        this.invoker = invoker;
        this.templateEngine = templateEngine;
        this.config = config;
    }

    /**
     * @param status status HTTP a renderizar (ex: 404, 500, 403)
     * @param path   path da requisicao que originou o erro, para o campo
     *               reservado {@code errorPath} - ou {@code null} se
     *               desconhecido/nao aplicavel
     * @param detail detalhe opcional do erro, para o campo reservado
     *               {@code errorDetail} - tipicamente {@code null} em
     *               producao (evitar vazar detalhe interno; o dev decide
     *               se quer expor via o proprio template)
     * @return o documento HTML completo, ou {@link Optional#empty()} se
     *         nenhum componente {@code @ErrorPage} esta registrado para
     *         este status - o adaptador decide o fallback nesse caso.
     */
    public Optional<String> render(int status, String path, String detail) {
        Optional<ComponentMetadata> metadata = registry.errorPage(status);
        if (metadata.isEmpty()) {
            return Optional.empty();
        }
        ComponentMetadata page = metadata.get();

        Object instance;
        try {
            instance = invoker.instantiate(Class.forName(page.fqn()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Classe do componente de erro nao encontrada: " + page.fqn(), e);
        }
        invoker.applyErrorInfo(instance, status, path, detail);
        invoker.callInitIfPresent(instance);

        StringOutput output = new StringOutput();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("self", instance);
        params.put("__jtaInvoker", invoker);
        templateEngine.render(page.generatedJteTemplate(), params, output);
        String pageHtml = output.toString();

        String bodyHtml = page.hasLayout() ? renderWithLayout(page, pageHtml) : pageHtml;
        return Optional.of(PageShellRenderer.wrap(bodyHtml, registry, config, null));
    }

    private String renderWithLayout(ComponentMetadata pageMetadata, String pageHtml) {
        ComponentMetadata layoutMetadata = registry.byFqn(pageMetadata.layoutFqn());

        Object layoutInstance;
        try {
            layoutInstance = invoker.instantiate(Class.forName(layoutMetadata.fqn()));
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Classe do layout nao encontrada: " + layoutMetadata.fqn(), e);
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("self", layoutInstance);
        params.put("content", pageHtml);
        params.put("__jtaInvoker", invoker);

        StringOutput layoutOutput = new StringOutput();
        templateEngine.render(layoutMetadata.generatedJteTemplate(), params, layoutOutput);
        return layoutOutput.toString();
    }
}
