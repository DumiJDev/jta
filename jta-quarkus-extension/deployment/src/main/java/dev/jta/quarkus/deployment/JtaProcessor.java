package dev.jta.quarkus.deployment;

import dev.jta.core.ComponentMetadata;
import dev.jta.core.ComponentRegistry;
import dev.jta.quarkus.JtaRecorder;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.vertx.http.deployment.RouteBuildItem;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.handler.BodyHandler;

import java.util.regex.Pattern;

/**
 * Le o {@link ComponentRegistry} em build-time (mesma leitura de
 * {@code META-INF/jta/components.json} que o runtime faz, so que agora
 * durante a augmentation) e produz um {@link RouteBuildItem} por
 * {@code @Route}, via o {@link JtaRecorder} do modulo runtime.
 *
 * <p>Nenhuma classe deste modulo entra no jar final da aplicacao - so
 * decide, em build-time, quais rotas existem e qual handler cada uma usa.
 *
 * <p>Reflection nativa (GraalVM): nao duplicado aqui de proposito - o
 * {@code META-INF/native-image/.../reflect-config.json} ja gerado pelo
 * {@code jta-processor} e automaticamente agregado pelo build nativo do
 * Quarkus, mesmo mecanismo usado por qualquer build nativo GraalVM.
 */
class JtaProcessor {

    private static final String FEATURE = "jta";
    private static final Pattern PATH_VARIABLE = Pattern.compile("\\{([a-zA-Z][a-zA-Z0-9_]*)\\}");

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem beans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClass("dev.jta.quarkus.QuarkusComponentFactory")
                .addBeanClass("dev.jta.quarkus.JtaCdiProducers")
                .setUnremovable()
                .build();
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void routes(JtaRecorder recorder, BuildProducer<RouteBuildItem> routes) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ComponentRegistry registry = ComponentRegistry.loadFromClasspath(classLoader);

        for (ComponentMetadata page : registry.pages()) {
            String vertxPath = toVertxPath(page.routePath());
            routes.produce(RouteBuildItem.builder()
                    .routeFunction(vertxPath, route -> route.method(HttpMethod.GET))
                    .handler(recorder.createPageHandler(page.selector()))
                    .build());
        }

        // RouteBuildItem.Builder nao tem metodo para restringir por metodo
        // HTTP nem para acoplar um BodyHandler diretamente - route() sozinho
        // aceita QUALQUER metodo (bug real, nao so cosmetico: um DELETE ou
        // PUT batendo em /__jta/action/:selector tambem seria despachado).
        // routeFunction(path, Consumer<Route>) da acesso ao Route real do
        // Vert.x Web para restringir o metodo; o BodyHandler precisa ser uma
        // rota separada registrada ANTES da rota de acao, ja que o Vert.x
        // Router executa, em ordem de registro, toda rota cujo path+metodo
        // casem (o mesmo jeito usado para acoplar parsing de corpo a uma
        // rota especifica em qualquer app Vert.x Web) - sem ele,
        // ctx.request().formAttributes() em JtaActionRouteHandler viria
        // sempre vazio.
        String actionPath = toVertxPath("/__jta/action/{selector}");
        routes.produce(RouteBuildItem.builder()
                .routeFunction(actionPath, route -> route.method(HttpMethod.POST))
                .handler(BodyHandler.create())
                .build());
        routes.produce(RouteBuildItem.builder()
                .routeFunction(actionPath, route -> route.method(HttpMethod.POST))
                .handler(recorder.createActionHandler())
                .build());
    }

    /** {@code "/produto/{id}"} (convencao @Route, igual ao Spring/Javalin) -> {@code "/produto/:id"} (Vert.x Web). */
    private static String toVertxPath(String jtaRoutePath) {
        return PATH_VARIABLE.matcher(jtaRoutePath).replaceAll(":$1");
    }
}
