package dev.jta.runtime;

/**
 * Instancia um componente JTA a partir da sua {@link Class}. Ponto de
 * extensao para hosts com container de DI (ex: Spring, que precisa
 * consultar seu {@code ApplicationContext} e travar contra bean singleton
 * indevido - ver {@code SpringComponentFactory} em jta-spring-boot-starter)
 * versus hosts sem nenhum container (ver {@link ReflectionComponentFactory}).
 *
 * <p>Existe para que {@link ComponentInvoker} - e por consequencia
 * {@link JtaActionDispatcher}/{@link JtaPageDispatcher} - nunca precisem
 * saber qual framework web/DI esta por baixo.
 */
public interface ComponentFactory {

    Object instantiate(Class<?> type);
}
