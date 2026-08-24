package dev.jta.standalone;

import com.sun.net.httpserver.HttpExchange;
import dev.jta.runtime.ComponentFactory;
import dev.jta.runtime.CurrentUser;
import dev.jta.runtime.ReflectionComponentFactory;
import jakarta.validation.Validator;

import java.util.function.Function;

/**
 * Configuracao do adaptador standalone: sem container de DI e sem modelo de
 * autenticacao nenhum por baixo (so {@code com.sun.net.httpserver.HttpServer}),
 * entao - igual ao adaptador Javalin - nao ha bean nenhum para descobrir
 * automaticamente. Defaults seguros para o caso mais simples: apenas
 * construtor sem argumentos, usuario sempre anonimo.
 */
public final class JtaStandaloneConfig {

    private ComponentFactory componentFactory = new ReflectionComponentFactory();
    private Function<HttpExchange, CurrentUser> currentUserResolver = exchange -> CurrentUser.anonymous();
    private Validator validator;

    public static JtaStandaloneConfig create() {
        return new JtaStandaloneConfig();
    }

    public JtaStandaloneConfig componentFactory(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        return this;
    }

    public JtaStandaloneConfig currentUserResolver(Function<HttpExchange, CurrentUser> currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
        return this;
    }

    public JtaStandaloneConfig validator(Validator validator) {
        this.validator = validator;
        return this;
    }

    ComponentFactory componentFactory() {
        return componentFactory;
    }

    Function<HttpExchange, CurrentUser> currentUserResolver() {
        return currentUserResolver;
    }

    Validator validator() {
        return validator;
    }
}
