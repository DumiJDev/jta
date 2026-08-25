package dev.jta.quarkus;

import io.quarkus.runtime.annotations.Recorder;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.RoutingContext;

import java.util.function.Consumer;

/**
 * Recorder usado pelo modulo deployment para produzir os handlers Vert.x
 * de cada {@code RouteBuildItem} - a instanciacao real (o construtor de
 * {@link JtaPageRouteHandler}/{@link JtaActionRouteHandler}/
 * {@link JtaSseRouteHandler}) acontece na fase de {@code RUNTIME_INIT} da
 * aplicacao, nao durante o build.
 */
@Recorder
public class JtaRecorder {

    public Handler<RoutingContext> createPageHandler(String selector) {
        return new JtaPageRouteHandler(selector);
    }

    public Handler<RoutingContext> createActionHandler() {
        return new JtaActionRouteHandler();
    }

    public Handler<RoutingContext> createSseHandler(String ssePath) {
        return new JtaSseRouteHandler(ssePath);
    }

    /**
     * Fabrica de {@code Consumer<Route>} para restringir uma rota a um
     * metodo HTTP, para uso em {@code RouteBuildItem.Builder#routeFunction}
     * (modulo deployment, {@code JtaProcessor}).
     *
     * <p>Precisa ser um metodo de INSTANCIA chamado atraves do proxy de
     * bytecode-recording (o parametro {@code recorder} injetado no
     * {@code @BuildStep}, nao uma chamada estatica direta) - duas
     * armadilhas reais encontradas rodando o TCK do Quarkus pela primeira
     * vez, nesta ordem:
     * <ol>
     *   <li>Uma lambda {@code route -> route.method(method)} declarada
     *   dentro do proprio {@code JtaProcessor} (modulo deployment) gera
     *   uma classe sintetica ancorada em {@code JtaProcessor} - invisivel
     *   ao classloader de runtime, onde as classes do modulo deployment
     *   nao existem mais ({@code NoClassDefFoundError}).</li>
     *   <li>Mesmo movendo a lambda para ca (modulo runtime) e expondo
     *   como fabrica {@code static}, chamada diretamente em build-time -
     *   {@code JtaRecorder.methodRestriction(...)} -, o objeto
     *   {@code Consumer} resultante ainda precisa ser serializado para
     *   bytecode pelo {@code BytecodeRecorderImpl} (a mesma maquina que
     *   grava {@code createPageHandler}/{@code createActionHandler}/
     *   {@code createSseHandler} abaixo), e uma lambda arbitraria nao tem
     *   construtor padrao para isso (RuntimeException: "Unable to
     *   serialize objects of type ... to bytecode as it has no default
     *   constructor"). Only a instancia devolvida por uma chamada via o
     *   proxy do recorder e "gravavel": o proxy nao executa o metodo
     *   agora, so grava "invoque JtaRecorder#methodRestriction(method)
     *   aqui" para rodar de verdade na fase RUNTIME_INIT.</li>
     *   <li>O ARGUMENTO tambem precisa ser de um tipo que o recorder saiba
     *   gravar como constante - {@link HttpMethod} do Vert.x nao e um
     *   {@code enum} Java de verdade (e uma classe com instancias
     *   estaticas, para suportar metodos HTTP customizados), entao passa-lo
     *   direto como argumento falha com
     *   {@code NullPointerException: ... "parameter" is null} dentro do
     *   {@code BytecodeRecorderImpl} (nenhuma substituicao conhecida para
     *   esse tipo). Um {@code String} (nome do metodo) e sempre gravavel;
     *   {@link HttpMethod#valueOf} reconstroi o valor real dentro do
     *   metodo, ja em runtime.</li>
     * </ol>
     */
    public Consumer<Route> methodRestriction(String httpMethodName) {
        HttpMethod method = HttpMethod.valueOf(httpMethodName);
        return route -> route.method(method);
    }
}
