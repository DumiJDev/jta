package dev.jta.runtime;

/**
 * {@link ComponentFactory} default para hosts sem container de DI
 * (standalone, ou um adaptador ainda sem suporte a injecao de servicos):
 * so chama o construtor sem argumentos. Componentes que precisam de DI via
 * construtor (ex: um {@code @Service} injetado) exigem um
 * {@link ComponentFactory} ciente do container do host - ver
 * {@code SpringComponentFactory} em jta-spring-boot-starter.
 */
public final class ReflectionComponentFactory implements ComponentFactory {

    @Override
    public Object instantiate(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Nao foi possivel instanciar o componente " + type.getName()
                    + " via construtor sem argumentos. Componentes com construtor que recebe argumentos (para DI) "
                    + "precisam de um ComponentFactory ciente do container do host.", e);
        }
    }
}
