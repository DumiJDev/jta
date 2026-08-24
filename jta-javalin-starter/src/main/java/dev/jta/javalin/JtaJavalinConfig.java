package dev.jta.javalin;

import dev.jta.runtime.ComponentFactory;
import dev.jta.runtime.CurrentUser;
import dev.jta.runtime.ReflectionComponentFactory;
import io.javalin.http.Context;
import jakarta.validation.Validator;

import java.util.function.Function;

/**
 * Configuracao do adaptador Javalin: Javalin nao tem container de DI nem
 * modelo de autenticacao proprio, entao (diferente do starter Spring) nao
 * ha bean nenhum para descobrir automaticamente - o consumidor passa
 * explicitamente como o host resolve componentes e o usuario autenticado.
 *
 * <p>Defaults seguros para o caso mais simples (nenhum DI, nenhuma
 * autenticacao): {@link ReflectionComponentFactory} e
 * {@code CurrentUser.anonymous()} para toda requisicao.
 */
public final class JtaJavalinConfig {

    private ComponentFactory componentFactory = new ReflectionComponentFactory();
    private Function<Context, CurrentUser> currentUserResolver = ctx -> CurrentUser.anonymous();
    private Validator validator;

    public static JtaJavalinConfig create() {
        return new JtaJavalinConfig();
    }

    public JtaJavalinConfig componentFactory(ComponentFactory componentFactory) {
        this.componentFactory = componentFactory;
        return this;
    }

    public JtaJavalinConfig currentUserResolver(Function<Context, CurrentUser> currentUserResolver) {
        this.currentUserResolver = currentUserResolver;
        return this;
    }

    public JtaJavalinConfig validator(Validator validator) {
        this.validator = validator;
        return this;
    }

    ComponentFactory componentFactory() {
        return componentFactory;
    }

    Function<Context, CurrentUser> currentUserResolver() {
        return currentUserResolver;
    }

    Validator validator() {
        return validator;
    }
}
