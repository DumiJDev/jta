package dev.jta.runtime;

import dev.jta.core.ComponentRegistry;
import dev.jta.core.JtaConfig;
import dev.jta.runtime.csrf.CsrfToken;

import java.net.URL;
import java.net.URLClassLoader;

public class PageShellTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        ClassLoader emptyClassLoader = new URLClassLoader(new URL[]{new java.io.File(args[0]).toURI().toURL()});
        ClassLoader styledClassLoader = new URLClassLoader(new URL[]{new java.io.File(args[1]).toURI().toURL()});

        ComponentRegistry registryVazio = ComponentRegistry.loadFromClasspath(emptyClassLoader);
        ComponentRegistry registryEstilizado = ComponentRegistry.loadFromClasspath(styledClassLoader);

        // sem [features] tailwindcss - deve usar o CSS de base embutido, sem script do Tailwind
        JtaConfig semTailwind = JtaConfig.empty();
        String semTailwindHtml = PageShellRenderer.wrap("<p>oi</p>", registryVazio, semTailwind, null);
        checkTrue("sem config: inclui BASE_CSS (var --jta-primary)", semTailwindHtml.contains("--jta-primary"));
        checkTrue("sem config: NAO inclui script do Tailwind", !semTailwindHtml.contains("cdn.tailwindcss.com"));
        checkTrue("sem config: inclui HTMX", semTailwindHtml.contains("htmx.org"));

        // com [features] tailwindcss = true - deve pular o CSS de base e incluir o script do Tailwind
        JtaConfig comTailwind = JtaConfig.parse("[features]\ntailwindcss = true\n");
        String comTailwindHtml = PageShellRenderer.wrap("<p>oi</p>", registryVazio, comTailwind, null);
        checkTrue("com tailwindcss=true: NAO inclui BASE_CSS", !comTailwindHtml.contains("--jta-primary"));
        checkTrue("com tailwindcss=true: inclui script do Tailwind", comTailwindHtml.contains("cdn.tailwindcss.com"));
        checkTrue("com tailwindcss=true: continua incluindo HTMX", comTailwindHtml.contains("htmx.org"));

        // CSS por componente continua indo nos dois casos
        String htmlComEstilo = PageShellRenderer.wrap("<p>oi</p>", registryEstilizado, comTailwind, null);
        checkTrue("CSS por componente ainda aparece mesmo com tailwindcss=true",
                htmlComEstilo.contains("color: hotpink"));

        // SECURITY.md achado #6 (CSRF nativo): csrfToken == null (ex: csrf_mode=disabled)
        // nao emite hx-headers nenhum; um CsrfToken presente emite o valor exato no atributo.
        checkTrue("sem csrfToken: NAO inclui hx-headers", !semTailwindHtml.contains("hx-headers"));
        CsrfToken token = new CsrfToken("X-JTA-CSRF-Token", "abc123");
        String comCsrfHtml = PageShellRenderer.wrap("<p>oi</p>", registryVazio, semTailwind, token);
        checkTrue("com csrfToken: inclui hx-headers com o header/valor exatos",
                comCsrfHtml.contains("hx-headers='{\"X-JTA-CSRF-Token\":\"abc123\"}'"));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void checkTrue(String label, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("PASS  " + label);
        } else {
            failed++;
            System.out.println("FAIL  " + label);
        }
    }
}
