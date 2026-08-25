package dev.jta.processor;

import dev.jta.core.AComponent;
import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentMetadataIo;
import dev.jta.core.JtaConfig;
import dev.jta.core.Layout;
import dev.jta.core.AllowAnonymous;
import dev.jta.core.CsrfExempt;
import dev.jta.core.RequiresRole;
import dev.jta.core.Route;
import dev.jta.core.Sse;
import dev.jta.core.SelectorDerivation;
import dev.jta.template.CssScoper;
import dev.jta.template.DidYouMean;
import dev.jta.template.TemplateTransformer;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.StandardLocation;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Le todas as classes {@code @AComponent}/{@code @Layout} do modulo sendo
 * compilado, resolve/valida o seletor de cada uma, transforma o template
 * (ver {@link TemplateTransformer} para o escopo exato dessa transformacao)
 * e emite:
 * <ul>
 *   <li>um arquivo {@code .jte} gerado por componente, para o
 *       {@code jte-maven-plugin} compilar em seguida;</li>
 *   <li>{@code META-INF/jta/components.json} com os metadados de todos os
 *       componentes do modulo, consumido em runtime por
 *       {@code ComponentRegistry} (starters).</li>
 * </ul>
 *
 * <p>Colisao de seletor explicito e detectada dentro deste modulo via
 * {@link RoundEnvironment} (que enxerga todas as classes sendo
 * compiladas agora); colisao entre jars independentes e detectada em
 * runtime por {@code ComponentRegistry} - ver nota na pom do modulo.
 */
@SupportedAnnotationTypes({"dev.jta.core.AComponent", "dev.jta.core.Layout"})
@SupportedSourceVersion(SourceVersion.RELEASE_21)
@SupportedOptions({
        JtaAnnotationProcessor.OPTION_RESOURCES_DIR,
        // Declara este processor como "aggregating" para o annotation
        // processing incremental do Gradle. Sem esta declaracao o Gradle
        // trata-o como nao-incremental e reprocessa o modulo inteiro a cada
        // alteracao, com um aviso no log. "Aggregating" (e nao "isolating")
        // e a categoria correta: a deteccao de colisao de selector precisa
        // de ver todos os @AComponent da mesma ronda (ver selectorToFqn),
        // o que e incompativel com o modo por-ficheiro do "isolating".
        // O Maven ignora opcoes que nao conhece, portanto isto e inerte la.
        "org.gradle.annotation.processing.aggregating"
})
public class JtaAnnotationProcessor extends AbstractProcessor {

    /**
     * Diretorio(s) de recursos do modulo a compilar, separados por
     * {@code ,} ou pelo separador de path do sistema.
     *
     * <p>Existe por causa do Gradle. Todas as leituras de recurso deste
     * processor (config, i18n, templateUrl/styleUrl) assumiam
     * {@code StandardLocation.CLASS_OUTPUT} - o que so funciona no Maven,
     * onde {@code target/classes} e partilhado entre a copia de recursos e
     * a compilacao. No Gradle, {@code build/resources/main} e
     * {@code build/classes/java/main} sao diretorios distintos, e
     * {@code Filer.getResource} sobre CLASS_OUTPUT nao encontra la nada
     * (ver gradle/gradle#7588). Como as leituras falhavam de forma
     * graciosa, o resultado nao era um erro de build mas uma app compilada
     * silenciosamente sem config nenhuma.
     *
     * <p>Quando esta opcao e passada, ela e <b>autoritativa</b>: o
     * fallback para o {@code Filer} nao e tentado, para que um ficheiro em
     * falta seja um erro visivel em vez de voltar a cair no caminho que
     * nao funciona nesse ambiente.
     */
    static final String OPTION_RESOURCES_DIR = "jta.resourcesDir";

    // {id} em @Route("/produto/{id}") - nome do path variable
    private static final Pattern ROUTE_PARAM = Pattern.compile("\\{([a-zA-Z_][a-zA-Z0-9_]*)\\}");

    // <router-outlet/> ou <router-outlet></router-outlet> - marcador de onde
    // o conteudo da pagina entra dentro do template de um @Layout
    private static final Pattern ROUTER_OUTLET =
            Pattern.compile("<router-outlet\\s*/?\\s*>(\\s*</router-outlet>)?");

    private final Map<String, String> selectorToFqn = new HashMap<>();
    private final List<ComponentMetadata> allMetadata = new ArrayList<>();

    private Messager messager;
    private Filer filer;
    private Elements elementUtils;
    private JtaConfig config;
    private Set<String> messageKeys;
    private List<Path> resourceDirs;

    @Override
    public synchronized void init(javax.annotation.processing.ProcessingEnvironment env) {
        super.init(env);
        this.messager = env.getMessager();
        this.filer = env.getFiler();
        this.elementUtils = env.getElementUtils();
    }

    /**
     * Le {@code jta.config.toml} do modulo sendo compilado (via
     * {@code StandardLocation.CLASS_OUTPUT}, ja que recursos em
     * {@code src/main/resources} ja foram copiados para
     * {@code target/classes} antes da fase de compilacao rodar). Lido
     * uma vez, sob demanda (na primeira chamada), nao em {@link #init}
     * diretamente - alguns compiladores/IDEs ainda nao tem o Filer
     * totalmente pronto para leitura nesse ponto.
     */
    private JtaConfig config() {
        if (config == null) {
            try {
                config = JtaConfig.parse(readModuleResource("jta.config.toml"));
            } catch (IOException e) {
                // jta.config.toml e opcional - ausencia cai graciosamente nos defaults.
                config = JtaConfig.empty();
            }
        }
        return config;
    }

    /**
     * Le as chaves de {@code messages.properties} do modulo sendo
     * compilado (mesma premissa de {@link #config()}: recursos ja
     * copiados para {@code target/classes} antes da compilacao rodar).
     * Usado para validar {@code {{ 'chave' | translate }}} em compile-time
     * - i18n e opcional, entao a ausencia do arquivo so significa que
     * nenhuma chave e valida (qualquer uso de {@code | translate} vai
     * falhar o build, o que e o comportamento correto: se nao ha bundle,
     * nao ha chave nenhuma para usar).
     */
    private Set<String> messageKeys() {
        if (messageKeys == null) {
            messageKeys = new java.util.HashSet<>();
            try {
                java.util.Properties props = new java.util.Properties();
                props.load(new java.io.StringReader(readModuleResource("messages.properties")));
                for (Object key : props.keySet()) {
                    messageKeys.add(key.toString());
                }
            } catch (IOException e) {
                // messages.properties e opcional - ausencia cai graciosamente (nenhuma chave valida).
            }
        }
        return messageKeys;
    }

    /**
     * Le um recurso do modulo a ser compilado, pelo caminho relativo a raiz
     * de recursos (ex: {@code "jta.config.toml"},
     * {@code "jta-templates/com/exemplo/Card.jta"}).
     *
     * <p>Duas estrategias, nesta ordem:
     * <ol>
     *   <li>Se {@code -Ajta.resourcesDir} foi passado (caminho do Gradle),
     *       le direto do filesystem. E autoritativo: nao ha fallback, para
     *       que ficheiro em falta seja erro visivel.</li>
     *   <li>Caso contrario (caminho do Maven), le via {@code Filer} sobre
     *       {@code CLASS_OUTPUT}, onde os recursos ja foram copiados pela
     *       fase anterior do build.</li>
     * </ol>
     *
     * @throws IOException se o recurso nao existir ou nao puder ser lido -
     *         cada chamador decide se isso e opcional (config/i18n) ou erro
     *         de compilacao (templateUrl/styleUrl).
     */
    private String readModuleResource(String relativePath) throws IOException {
        List<Path> dirs = resourceDirs();
        if (!dirs.isEmpty()) {
            for (Path dir : dirs) {
                Path candidate = dir.resolve(relativePath);
                if (Files.isRegularFile(candidate)) {
                    return Files.readString(candidate, StandardCharsets.UTF_8);
                }
            }
            throw new FileNotFoundException(
                    relativePath + " nao encontrado em nenhum dos diretorios de recursos declarados por -A"
                            + OPTION_RESOURCES_DIR + " (" + dirs + ")");
        }
        FileObject resource = filer.getResource(StandardLocation.CLASS_OUTPUT, "", relativePath);
        return resource.getCharContent(true).toString();
    }

    private List<Path> resourceDirs() {
        if (resourceDirs == null) {
            String raw = processingEnv.getOptions().get(OPTION_RESOURCES_DIR);
            if (raw == null || raw.isBlank()) {
                resourceDirs = List.of();
            } else {
                List<Path> dirs = new ArrayList<>();
                for (String part : raw.split("[,;" + java.io.File.pathSeparator + "]")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) {
                        dirs.add(Path.of(trimmed));
                    }
                }
                resourceDirs = List.copyOf(dirs);
            }
        }
        return resourceDirs;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(AComponent.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@AComponent so pode ser aplicado a classes", element);
                continue;
            }
            safely(element, () -> processComponent((TypeElement) element));
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(Layout.class)) {
            if (element.getKind() != ElementKind.CLASS) {
                messager.printMessage(Diagnostic.Kind.ERROR, "@Layout so pode ser aplicado a classes", element);
                continue;
            }
            safely(element, () -> processLayout((TypeElement) element));
        }

        if (roundEnv.processingOver() && !allMetadata.isEmpty()) {
            writeMetadataFile();
        }
        return true;
    }

    /**
     * Roda {@code action} capturando falhas de resolucao de valor de
     * anotacao (acontece quando outro erro de compilacao no mesmo modulo
     * impede uma expressao constante de ser resolvida - ex: "cannot find
     * symbol" numa classe referenciada dentro da concatenacao do
     * template/style) - sem isso, o processor trava com uma excecao nao
     * tratada e uma mensagem generica em vez de apontar para o problema
     * real.
     */
    private void safely(Element element, Runnable action) {
        try {
            action.run();
        } catch (java.lang.annotation.AnnotationTypeMismatchException | java.lang.annotation.IncompleteAnnotationException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "[JTA] nao foi possivel ler os atributos desta anotacao. Isso normalmente acontece quando ha "
                            + "OUTRO erro de compilacao no mesmo modulo que impede um valor constante (usado em "
                            + "template()/templateUrl()/style()/styleUrl()/selector()) de ser resolvido - procure "
                            + "por erros de compilacao ANTERIORES a este no output do javac (frequentemente um "
                            + "'cannot find symbol'). Detalhe: " + e.getMessage(),
                    element);
        }
    }

    private void processComponent(TypeElement type) {
        AComponent annotation = type.getAnnotation(AComponent.class);
        String fqn = elementUtils.getBinaryName(type).toString();

        String selector = resolveSelector(annotation.selector(), fqn, type);
        if (selector == null) {
            return; // erro ja reportado (colisao de selector explicito)
        }
        boolean explicit = annotation.selector() != null && !annotation.selector().isBlank();

        String rawTemplate = resolveTemplate(annotation.template(), annotation.templateUrl(), type, "@AComponent");
        if (rawTemplate == null) {
            return;
        }
        String rawCss = resolveCss(annotation.style(), annotation.styleUrl(), type, "@AComponent");
        if (rawCss == null) {
            return;
        }

        FieldsAndMethods known = collectFieldsAndMethods(type);

        TemplateTransformer.Result result = TemplateTransformer.transform(
                rawTemplate, selector, known.fields(), known.templateMethods(), known.actions(), known.nullableFields(), messageKeys());
        if (!reportErrorsIfAny(result, type)) {
            return;
        }

        // --- rota e layout, se @Route presente ---
        Route route = type.getAnnotation(Route.class);
        String routePath = route != null ? route.value() : null;

        if (routePath != null && !validateRoutePathParams(routePath, known.fields(), type)) {
            return;
        }

        String layoutFqn = null;
        if (route != null) {
            layoutFqn = extractLayoutFqn(route);
            if (layoutFqn != null) {
                TypeElement layoutType = elementUtils.getTypeElement(layoutFqn);
                if (layoutType == null || layoutType.getAnnotation(Layout.class) == null) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "[JTA] @Route(layout = " + layoutFqn + ") nao aponta para uma classe anotada com @Layout",
                            type);
                    return;
                }
            }
        }

        String generatedRelativePath = writeGeneratedJte(fqn, type, "@param " + fqn + " self\n" + result.generatedJte());
        if (generatedRelativePath == null) {
            return;
        }

        String scopedCss = CssScoper.scope(rawCss, selector);

        Security security = resolveSecurity(type);
        if (security == null) {
            return; // erro ja reportado (roles invalidas, ou @RequiresRole + @AllowAnonymous juntos)
        }

        Sse sse = type.getAnnotation(Sse.class);
        String ssePath = sse != null ? sse.value() : null;
        long sseIntervalMillis = sse != null ? sse.intervalMillis() : 0;

        Set<String> bindableFields = new LinkedHashSet<>(result.referencedFields());
        bindableFields.addAll(known.explicitlyBindableFields());
        if (routePath != null) {
            Matcher routeParamMatcher = ROUTE_PARAM.matcher(routePath);
            while (routeParamMatcher.find()) {
                bindableFields.add(routeParamMatcher.group(1));
            }
        }
        if (!reportReservedFieldNameCollision(known.explicitlyBindableFields(), routePath, type)) {
            return;
        }
        // dev.jta.core.ReservedFieldNames.ALL - nomes que o runtime decide
        // (sessao/flash/erro), nunca o cliente. A checagem acima ja falhou
        // o build se algum desses nomes tivesse entrado por @Bindable ou
        // por ser um path param; esta remocao cobre so o caso de o
        // template simplesmente interpolar um campo com um desses nomes
        // (ex: {{ flashSuccess }}), que sem isto entraria na allowlist so
        // por ser referenciado - ver ReservedFieldNames para o cenario de
        // mass-assignment que isto evita.
        bindableFields.removeAll(dev.jta.core.ReservedFieldNames.ALL);

        boolean csrfExempt = type.getAnnotation(CsrfExempt.class) != null;

        allMetadata.add(new ComponentMetadata(
                fqn, selector, explicit, routePath, List.copyOf(known.actions()), generatedRelativePath,
                scopedCss, false, layoutFqn, security.roles(), security.allowAnonymous(), ssePath, sseIntervalMillis,
                List.copyOf(bindableFields), csrfExempt));
    }

    private record Security(List<String> roles, boolean allowAnonymous) {
    }

    /**
     * Resolve {@code @RequiresRole}/{@code @AllowAnonymous} numa classe.
     * Se {@code jta.config.toml} configurar {@code [security] roles_enum},
     * cada valor de {@code @RequiresRole} e validado contra as constantes
     * desse enum em compile-time (com sugestao "voce quis dizer X?") -
     * sem essa config, os valores sao aceitos como strings livres.
     */
    private Security resolveSecurity(TypeElement type) {
        RequiresRole requiresRole = type.getAnnotation(RequiresRole.class);
        AllowAnonymous allowAnonymous = type.getAnnotation(AllowAnonymous.class);

        if (requiresRole != null && allowAnonymous != null) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "[JTA] @RequiresRole e @AllowAnonymous sao contraditorios - use no maximo um dos dois.", type);
            return null;
        }
        if (requiresRole == null) {
            return new Security(List.of(), allowAnonymous != null);
        }

        List<String> roles = List.of(requiresRole.value());
        String rolesEnumFqn = config().getString("security", "roles_enum", "");
        if (!rolesEnumFqn.isBlank()) {
            TypeElement enumType = elementUtils.getTypeElement(rolesEnumFqn);
            if (enumType == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "[JTA] [security] roles_enum em jta.config.toml aponta para '" + rolesEnumFqn
                                + "', que nao foi encontrado.", type);
                return null;
            }
            Set<String> validRoles = new LinkedHashSet<>();
            for (Element member : enumType.getEnclosedElements()) {
                if (member.getKind() == ElementKind.ENUM_CONSTANT) {
                    validRoles.add(member.getSimpleName().toString());
                }
            }
            for (String role : roles) {
                if (!validRoles.contains(role)) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "[JTA] role '" + role + "' em @RequiresRole nao existe em " + rolesEnumFqn
                                    + DidYouMean.suggest(role, validRoles), type);
                    return null;
                }
            }
        }
        return new Security(roles, false);
    }

    /**
     * Processa uma classe {@code @Layout}: mesma resolucao de
     * template/CSS/bindings de um {@code @AComponent}, mais a validacao e
     * substituicao do marcador {@code <router-outlet/>} por um segundo
     * {@code @param} ({@code content}) que a camada de runtime preenche
     * com o HTML ja renderizado da pagina (ver {@code JtaRouteRegistrar}
     * no starter - a composicao acontece em runtime, nao aqui).
     */
    private void processLayout(TypeElement type) {
        Layout annotation = type.getAnnotation(Layout.class);
        String fqn = elementUtils.getBinaryName(type).toString();

        String selector = resolveSelector(null, fqn, type);
        if (selector == null) {
            return;
        }

        String rawTemplate = resolveTemplate(annotation.template(), annotation.templateUrl(), type, "@Layout");
        if (rawTemplate == null) {
            return;
        }
        String rawCss = resolveCss(annotation.style(), annotation.styleUrl(), type, "@Layout");
        if (rawCss == null) {
            return;
        }

        FieldsAndMethods known = collectFieldsAndMethods(type);

        TemplateTransformer.Result result = TemplateTransformer.transform(
                rawTemplate, selector, known.fields(), known.templateMethods(), known.actions(), known.nullableFields(), messageKeys());
        if (!reportErrorsIfAny(result, type)) {
            return;
        }

        String jteWithOutlet = substituteRouterOutlet(result.generatedJte(), type);
        if (jteWithOutlet == null) {
            return; // erro ja reportado (zero ou mais de um <router-outlet/>)
        }

        String header = "@param " + fqn + " self\n@param String content\n";
        String generatedRelativePath = writeGeneratedJte(fqn, type, header + jteWithOutlet);
        if (generatedRelativePath == null) {
            return;
        }

        String scopedCss = CssScoper.scope(rawCss, selector);

        allMetadata.add(new ComponentMetadata(
                fqn, selector, false, null, List.copyOf(known.actions()), generatedRelativePath,
                scopedCss, true, null, List.of(), false, null, 0, List.of(), false));
    }

    private String resolveSelector(String explicitSelector, String fqn, TypeElement type) {
        boolean explicit = explicitSelector != null && !explicitSelector.isBlank();
        if (explicit) {
            String existingFqn = selectorToFqn.putIfAbsent(explicitSelector, fqn);
            if (existingFqn != null && !existingFqn.equals(fqn)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Selector '" + explicitSelector + "' ja e usado por " + existingFqn + ". Selectors "
                                + "explicitos devem ser unicos no projeto - remova o selector explicito (o seletor "
                                + "derivado do FQN nunca colide) ou use @Use(type=..., as=\"...\") no consumidor "
                                + "para um alias local.",
                        type);
                return null;
            }
            return explicitSelector;
        }
        boolean stripDomainPrefix = config().getBoolean("selector", "strip_domain_prefix", true);
        String separator = config().getString("selector", "separator", "-");
        String selector = SelectorDerivation.derive(fqn, stripDomainPrefix, separator);
        selectorToFqn.putIfAbsent(selector, fqn);
        return selector;
    }

    private String resolveTemplate(String template, String templateUrl, TypeElement type, String contextLabel) {
        if (!template.isBlank() && !templateUrl.isBlank()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    contextLabel + " nao pode declarar template() e templateUrl() ao mesmo tempo - escolha um.", type);
            return null;
        } else if (!template.isBlank()) {
            return template;
        } else if (!templateUrl.isBlank()) {
            return readExternalResource(templateUrl, type, "templateUrl");
        } else {
            messager.printMessage(Diagnostic.Kind.ERROR, contextLabel + " precisa de template() ou templateUrl()", type);
            return null;
        }
    }

    private String resolveCss(String style, String styleUrl, TypeElement type, String contextLabel) {
        if (!style.isBlank() && !styleUrl.isBlank()) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    contextLabel + " nao pode declarar style() e styleUrl() ao mesmo tempo - escolha um.", type);
            return null;
        } else if (!style.isBlank()) {
            return style;
        } else if (!styleUrl.isBlank()) {
            return readExternalResource(styleUrl, type, "styleUrl");
        } else {
            return "";
        }
    }

    private record FieldsAndMethods(Set<String> fields, Set<String> templateMethods, Set<String> actions,
                                     Set<String> nullableFields, Set<String> explicitlyBindableFields) {
    }

    private FieldsAndMethods collectFieldsAndMethods(TypeElement type) {
        Set<String> fields = new LinkedHashSet<>();
        Set<String> templateMethods = new LinkedHashSet<>();
        Set<String> actions = new LinkedHashSet<>();
        Set<String> nullableFields = new LinkedHashSet<>();
        Set<String> explicitlyBindableFields = new LinkedHashSet<>();

        for (Element member : type.getEnclosedElements()) {
            if (member.getModifiers().contains(Modifier.STATIC)) {
                continue;
            }
            if (member.getKind() == ElementKind.FIELD && member.getModifiers().contains(Modifier.PUBLIC)) {
                fields.add(member.getSimpleName().toString());
                if (hasAnnotationNamed(member, "Nullable")) {
                    nullableFields.add(member.getSimpleName().toString());
                }
                if (hasAnnotationNamed(member, "Bindable")) {
                    explicitlyBindableFields.add(member.getSimpleName().toString());
                }
            } else if (member.getKind() == ElementKind.METHOD && member.getModifiers().contains(Modifier.PUBLIC)) {
                ExecutableElement method = (ExecutableElement) member;
                if (!method.getParameters().isEmpty()) {
                    continue; // MVP: so metodos sem argumentos sao suportados no template
                }
                if (method.getReturnType().getKind() == TypeKind.VOID) {
                    actions.add(method.getSimpleName().toString());
                } else {
                    templateMethods.add(method.getSimpleName().toString());
                }
            }
        }
        return new FieldsAndMethods(fields, templateMethods, actions, nullableFields, explicitlyBindableFields);
    }

    /**
     * Verifica se {@code element} tem alguma anotacao cujo nome simples e
     * {@code simpleName}, independente do pacote - assim {@code @Nullable}
     * funciona vindo de JSpecify, {@code javax.annotation}, JetBrains
     * Annotations, etc., sem o jta-processor forcar uma dependencia
     * especifica (decisao de design: null-safety usa o que o dev ja tiver,
     * nao inventa anotacao propria).
     */
    private boolean hasAnnotationNamed(Element element, String simpleName) {
        for (var mirror : element.getAnnotationMirrors()) {
            if (mirror.getAnnotationType().asElement().getSimpleName().toString().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    private boolean reportErrorsIfAny(TemplateTransformer.Result result, TypeElement type) {
        if (result.hasErrors()) {
            for (TemplateTransformer.ValidationError error : result.errors()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "[JTA] " + error.message(), type);
            }
            return false;
        }
        return true;
    }

    /**
     * Erro de compilacao (fail-closed, nao exclusao silenciosa) quando um
     * campo {@code @Bindable} explicito ou um {@code {param}} de rota usa
     * um nome reservado ao runtime (ver {@link dev.jta.core.ReservedFieldNames}).
     *
     * <p>Diferente do campo so interpolado no template (esse e removido de
     * {@code bindableFields} em silencio, ver o chamador) - aqui o dev
     * pediu explicitamente para o campo ser bindavel a partir da
     * requisicao, o que e um sinal forte demais de intencao para ignorar
     * sem avisar: melhor falhar o build agora, com uma mensagem clara, do
     * que deixar o dev descobrir mais tarde que o nome colide com um campo
     * que uma feature futura do proprio JTA (sessao/flash/erro) vai
     * injetar.
     */
    private boolean reportReservedFieldNameCollision(Set<String> explicitlyBindableFields, String routePath, TypeElement type) {
        boolean ok = true;
        for (String name : explicitlyBindableFields) {
            if (dev.jta.core.ReservedFieldNames.ALL.contains(name)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "[JTA] '" + name + "' e um nome de campo reservado ao runtime do JTA (ver "
                                + "dev.jta.core.ReservedFieldNames) e nao pode ser declarado @Bindable - escolha "
                                + "outro nome para este campo.",
                        type);
                ok = false;
            }
        }
        if (routePath != null) {
            Matcher routeParamMatcher = ROUTE_PARAM.matcher(routePath);
            while (routeParamMatcher.find()) {
                String name = routeParamMatcher.group(1);
                if (dev.jta.core.ReservedFieldNames.ALL.contains(name)) {
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "[JTA] '{" + name + "}' em @Route(\"" + routePath + "\") usa um nome de campo "
                                    + "reservado ao runtime do JTA (ver dev.jta.core.ReservedFieldNames) - escolha "
                                    + "outro nome para este path variable.",
                            type);
                    ok = false;
                }
            }
        }
        return ok;
    }

    /**
     * Extrai o FQN de {@code @Route(layout = MeuLayout.class)}.
     *
     * <p>Chamar {@code route.layout()} diretamente e o jeito errado de
     * fazer isso dentro de um annotation processor: o valor {@code Class<?>}
     * de uma anotacao pode se referir a uma classe que ainda nao foi
     * compilada, entao a JVM nao consegue carregar esse {@code Class} de
     * verdade e lanca {@link MirroredTypeException} em vez de devolver o
     * valor - e o {@link TypeMirror} dentro da excecao (nao o valor de
     * retorno) que tem a informacao real. Esse e um dos erros classicos
     * mais comuns ao escrever annotation processors.
     */
    private String extractLayoutFqn(Route route) {
        TypeMirror mirror;
        try {
            Class<?> layoutClass = route.layout();
            return layoutClass == Void.class ? null : layoutClass.getCanonicalName();
        } catch (MirroredTypeException e) {
            mirror = e.getTypeMirror();
        }
        String typeName = mirror.toString();
        return "java.lang.Void".equals(typeName) ? null : typeName;
    }

    /**
     * Substitui o unico {@code <router-outlet/>} esperado por
     * {@code $unsafe{content}} (a sintaxe do JTE para saida sem
     * escapamento - o conteudo ja e HTML renderizado, escapar de novo
     * bagunçaria a saida). Erra se houver zero ou mais de uma ocorrencia.
     */
    private String substituteRouterOutlet(String jte, TypeElement type) {
        Matcher m = ROUTER_OUTLET.matcher(jte);
        int count = 0;
        while (m.find()) {
            count++;
        }
        if (count == 0) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "[JTA] @Layout precisa de exatamente um <router-outlet/> no template - nenhum encontrado.", type);
            return null;
        }
        if (count > 1) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "[JTA] @Layout precisa de exatamente um <router-outlet/> no template - " + count
                            + " encontrados. Layouts aninhados nao sao suportados nesta versao.", type);
            return null;
        }
        return ROUTER_OUTLET.matcher(jte).replaceFirst(Matcher.quoteReplacement("$unsafe{content}"));
    }

    private String writeGeneratedJte(String fqn, TypeElement type, String content) {
        String generatedRelativePath = fqn.replace('.', '/') + ".jte";
        try {
            FileObject jteFile = filer.createResource(StandardLocation.SOURCE_OUTPUT, "", "jta-templates/" + generatedRelativePath, type);
            try (Writer writer = jteFile.openWriter()) {
                writer.write(content);
            }
            return generatedRelativePath;
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "[JTA] falha ao escrever .jte gerado: " + e.getMessage(), type);
            return null;
        }
    }

    private void writeMetadataFile() {
        try {
            FileObject out = filer.createResource(StandardLocation.CLASS_OUTPUT, "", "META-INF/jta/components.json");
            try (Writer writer = out.openWriter()) {
                writer.write(ComponentMetadataIo.toJson(allMetadata));
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "[JTA] falha ao escrever components.json: " + e.getMessage());
        }
        writeNativeImageConfig();
    }

    /**
     * Emite {@code META-INF/native-image/.../reflect-config.json} listando
     * toda classe {@code @AComponent}/{@code @Layout} processada neste
     * modulo. GraalVM {@code native-image} nao enxerga reflection em
     * closed-world analysis por padrao - e {@code JtaComponentInvoker}
     * usa reflection extensivamente (construtor, campos, metodos) para
     * instanciar/popular/invocar componentes. Sem esse arquivo, qualquer
     * app JTA compilado nativo quebraria em runtime com
     * {@code NoSuchMethodException}/{@code IllegalAccessException} - o
     * processor ja sabe exatamente quais classes/membros importam (a
     * mesma analise usada para gerar o .jte), entao gerar isso
     * automaticamente elimina a necessidade do dev manter esse arquivo
     * na mao.
     *
     * <p>Usa as flags "all*" (todos os campos/metodos/construtores
     * publicos e declarados) em vez de listar assinaturas individuais -
     * mais simples e robusto a mudancas no componente do que enumerar
     * cada metodo com seus tipos de parametro exatos.
     *
     * <p><b>Nao verificado neste ambiente</b> - escrever o JSON e testavel
     * sem GraalVM instalado, mas rodar {@code native-image} de verdade
     * contra o resultado nao foi possivel aqui (sem acesso a rede para
     * baixar o GraalVM).
     */
    private void writeNativeImageConfig() {
        if (allMetadata.isEmpty()) {
            return;
        }
        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < allMetadata.size(); i++) {
            ComponentMetadata metadata = allMetadata.get(i);
            json.append("  {\n")
                    .append("    \"name\": \"").append(metadata.fqn()).append("\",\n")
                    .append("    \"allDeclaredFields\": true,\n")
                    .append("    \"allPublicFields\": true,\n")
                    .append("    \"allDeclaredMethods\": true,\n")
                    .append("    \"allPublicMethods\": true,\n")
                    .append("    \"allDeclaredConstructors\": true,\n")
                    .append("    \"allPublicConstructors\": true\n")
                    .append("  }");
            json.append(i < allMetadata.size() - 1 ? ",\n" : "\n");
        }
        json.append("]\n");

        try {
            FileObject out = filer.createResource(StandardLocation.CLASS_OUTPUT, "",
                    "META-INF/native-image/dev.jta/jta-generated/reflect-config.json");
            try (Writer writer = out.openWriter()) {
                writer.write(json.toString());
            }
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR, "[JTA] falha ao escrever reflect-config.json: " + e.getMessage());
        }
    }

    /**
     * Valida que todo {@code {param}} em {@code @Route("...")} corresponde
     * a um campo publico do componente - o path variable e reidratado no
     * mesmo campo pelo mesmo mecanismo de "estado gerenciado pelo backend"
     * usado para query params (ver {@code JtaComponentInvoker} no starter),
     * entao um path param sem campo correspondente e sempre um erro, nunca
     * so um aviso.
     */
    private boolean validateRoutePathParams(String routePath, Set<String> fields, TypeElement type) {
        boolean valid = true;
        Matcher m = ROUTE_PARAM.matcher(routePath);
        while (m.find()) {
            String param = m.group(1);
            if (!fields.contains(param)) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "[JTA] parametro de rota '" + param + "' em @Route(\"" + routePath + "\") nao corresponde "
                                + "a nenhum campo publico do componente" + DidYouMean.suggest(param, fields),
                        type);
                valid = false;
            }
        }
        return valid;
    }

    /**
     * Le um recurso externo declarado via {@code @AComponent(templateUrl = "Nome.jta")}
     * ou {@code @AComponent(styleUrl = "Nome.css")} (ou os equivalentes de {@code @Layout}).
     *
     * <p>Convencao: o arquivo vive em
     * {@code src/main/resources/jta-templates/<pacote-do-componente>/<nome>},
     * espelhando onde o {@code .jte} gerado tambem e escrito. Lido via
     * {@code StandardLocation.CLASS_OUTPUT} porque, no momento em que a
     * compilacao com annotation processing roda, os recursos de
     * {@code src/main/resources} ja foram copiados para
     * {@code target/classes} pela fase de build anterior - a mesma
     * premissa usada para ler {@code jta.config.toml} (ver {@link #config()}).
     *
     * @param fileName nome do arquivo declarado em templateUrl()/styleUrl()
     * @param kind      "templateUrl" ou "styleUrl", so para a mensagem de erro
     * @return o conteudo do arquivo, ou {@code null} se nao pode ser lido
     *         (erro ja reportado ao {@link Messager} nesse caso)
     */
    private String readExternalResource(String fileName, TypeElement type, String kind) {
        String packagePath = elementUtils.getPackageOf(type).getQualifiedName().toString().replace('.', '/');
        String resourcePath = "jta-templates/" + (packagePath.isEmpty() ? "" : packagePath + "/") + fileName;
        try {
            return readModuleResource(resourcePath);
        } catch (IOException e) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "[JTA] nao foi possivel ler " + kind + " '" + fileName + "' - esperava encontrar o arquivo em "
                            + "src/main/resources/" + resourcePath + " (" + e.getMessage() + ")",
                    type);
            return null;
        }
    }
}
