import dev.jta.core.*;
import java.util.List;

public class SmokeTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) {
        check("kebab from package+class",
                SelectorDerivation.derive("com.acme.ui.components.UserCard"),
                "acme-ui-components-user-card");

        check("strips com prefix",
                SelectorDerivation.derive("com.acme.Button"),
                "acme-button");

        check("strips org prefix",
                SelectorDerivation.derive("org.acme.Button"),
                "acme-button");

        check("keeps prefix when disabled",
                SelectorDerivation.derive("com.acme.Button", false, "-"),
                "com-acme-button");

        check("custom separator",
                SelectorDerivation.derive("com.acme.ui.Button", true, "."),
                "acme.ui.button");

        String a = SelectorDerivation.derive("com.acme.widgets.Button");
        String b = SelectorDerivation.derive("com.other.widgets.Button");
        checkTrue("different FQNs never collide", !a.equals(b));

        String single = SelectorDerivation.derive("Button", true, "-");
        checkTrue("single-segment class still gets a hyphen (valid custom element)", single.contains("-"));

        // ComponentMetadata JSON round-trip (the exact contract between
        // JtaAnnotationProcessor at compile-time and ComponentRegistry at runtime)
        ComponentMetadata original = new ComponentMetadata(
                "dev.jta.demo.Contador",
                "jta-demo-contador",
                false,
                "/contador",
                List.of("incrementar", "resetar"),
                "dev/jta/demo/Contador.jte",
                "[data-jta-component=\"jta-demo-contador\"] h1 { color: blue; }",
                false,
                "dev.jta.demo.SiteLayout",
                List.of("ADMIN", "EDITOR"),
                false,
                "/live",
                5000L,
                List.of("valor", "titulo")
        );
        String json = ComponentMetadataIo.toJson(List.of(original));
        List<ComponentMetadata> parsed = ComponentMetadataIo.fromJson(json);
        checkTrue("json round-trip size", parsed.size() == 1);
        ComponentMetadata roundTripped = parsed.get(0);
        checkTrue("json round-trip fqn", roundTripped.fqn().equals(original.fqn()));
        checkTrue("json round-trip selector", roundTripped.selector().equals(original.selector()));
        checkTrue("json round-trip routePath", roundTripped.routePath().equals(original.routePath()));
        checkTrue("json round-trip actions", roundTripped.actions().equals(original.actions()));
        checkTrue("json round-trip isPage()", roundTripped.isPage());
        checkTrue("json round-trip scopedCss", roundTripped.scopedCss().equals(original.scopedCss()));
        checkTrue("hasStyle() true when scopedCss present", roundTripped.hasStyle());
        checkTrue("json round-trip layoutFqn", roundTripped.layoutFqn().equals(original.layoutFqn()));
        checkTrue("hasLayout() true when layoutFqn present", roundTripped.hasLayout());
        checkTrue("json round-trip isLayout false for a page", !roundTripped.isLayout());
        checkTrue("json round-trip requiredRoles", roundTripped.requiredRoles().equals(original.requiredRoles()));
        checkTrue("isRestricted() true when requiredRoles present", roundTripped.isRestricted());
        checkTrue("json round-trip allowAnonymous false", !roundTripped.allowAnonymous());
        checkTrue("json round-trip ssePath", roundTripped.ssePath().equals(original.ssePath()));
        checkTrue("json round-trip sseIntervalMillis", roundTripped.sseIntervalMillis() == original.sseIntervalMillis());
        checkTrue("hasSse() true when ssePath present", roundTripped.hasSse());
        checkTrue("json round-trip bindableFields", roundTripped.bindableFields().equals(original.bindableFields()));

        // component with no route (not a page) and no style
        ComponentMetadata partial = new ComponentMetadata(
                "dev.jta.demo.Button", "jta-demo-button", true, null, List.of(), "dev/jta/demo/Button.jte", null,
                false, null, List.of(), true, null, 0L, List.of());
        List<ComponentMetadata> parsedPartial = ComponentMetadataIo.fromJson(ComponentMetadataIo.toJson(List.of(partial)));
        checkTrue("null routePath round-trips as null", parsedPartial.get(0).routePath() == null);
        checkTrue("non-page isPage() is false", !parsedPartial.get(0).isPage());
        checkTrue("hasStyle() false when scopedCss is null", !parsedPartial.get(0).hasStyle());
        checkTrue("hasLayout() false when layoutFqn is null", !parsedPartial.get(0).hasLayout());
        checkTrue("isRestricted() false when requiredRoles is empty", !parsedPartial.get(0).isRestricted());
        checkTrue("json round-trip allowAnonymous true", parsedPartial.get(0).allowAnonymous());

        // a layout itself
        ComponentMetadata layout = new ComponentMetadata(
                "dev.jta.demo.SiteLayout", "dev-jta-demo-site-layout", false, null, List.of(),
                "dev/jta/demo/SiteLayout.jte", null, true, null, List.of(), false, null, 0L, List.of());
        List<ComponentMetadata> parsedLayout = ComponentMetadataIo.fromJson(ComponentMetadataIo.toJson(List.of(layout)));
        checkTrue("layout isLayout() true", parsedLayout.get(0).isLayout());
        checkTrue("layout isPage() false (no routePath)", !parsedLayout.get(0).isPage());

        // JtaConfig (jta.config.toml minimal parser)
        String toml = """
                # comentario deveria ser ignorado
                [selector]
                strip_domain_prefix = true
                separator = "-"

                [htmx]
                cdn_url = "https://unpkg.com/htmx.org@2.0.4" # comentario inline
                """;
        JtaConfig config = JtaConfig.parse(toml);
        checkTrue("toml boolean parsed", config.getBoolean("selector", "strip_domain_prefix", false));
        check("toml string parsed", config.getString("selector", "separator", "?"), "-");
        check("toml string with inline comment parsed", config.getString("htmx", "cdn_url", "?"),
                "https://unpkg.com/htmx.org@2.0.4");
        check("toml missing key falls back to default", config.getString("htmx", "nao_existe", "default"), "default");
        checkTrue("toml missing section falls back to default", config.getBoolean("nao_existe", "x", true));
        JtaConfig emptyConfig = JtaConfig.empty();
        check("empty config falls back to default", emptyConfig.getString("qualquer", "coisa", "default"), "default");

        // Redirect (sinal lancado de dentro de uma acao para navegar apos ela terminar)
        Redirect redirect = new Redirect("/produtos");
        checkTrue("Redirect.path()", redirect.path().equals("/produtos"));
        checkTrue("Redirect e RuntimeException (nao precisa de throws)", redirect instanceof RuntimeException);
        checkTrue("Redirect.getMessage() menciona o path", redirect.getMessage().contains("/produtos"));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void check(String label, String actual, String expected) {
        if (actual.equals(expected)) {
            passed++;
            System.out.println("PASS  " + label + "  => " + actual);
        } else {
            failed++;
            System.out.println("FAIL  " + label + "  expected=" + expected + " actual=" + actual);
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
