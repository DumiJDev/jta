package dev.jta.spring;

import dev.jta.runtime.ComponentFactory;
import org.springframework.context.ApplicationContext;

/**
 * {@link ComponentFactory} do adaptador Spring: se o dev registrou a
 * classe como bean Spring (para receber DI de servicos via construtor),
 * reusa a infra do Spring; senao cai para um construtor sem argumentos
 * simples.
 *
 * <p><b>Trava de correcao importante:</b> um componente JTA carrega
 * estado por requisicao (campos publicos populados a cada chamada). Se o
 * dev registrar a classe como {@code @Component} sem
 * {@code @Scope("prototype")}, o Spring devolve a MESMA instancia
 * singleton em toda requisicao - o estado de uma requisicao vazaria para
 * a proxima (bug de concorrencia silencioso e serio, o pior tipo de bug
 * para deixar passar batido). Por isso falhamos alto e cedo em vez de
 * deixar o dev descobrir isso sob carga em producao.
 *
 * <p>Extraido de {@code JtaComponentInvoker.instantiate}
 * (jta-spring-boot-starter) na extracao do nucleo agnostico - unica parte
 * daquela classe que era especifica do Spring.
 */
final class SpringComponentFactory implements ComponentFactory {

    private final ApplicationContext applicationContext;

    SpringComponentFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object instantiate(Class<?> type) {
        String[] beanNames = applicationContext.getBeanNamesForType(type);
        if (beanNames.length > 0) {
            String beanName = beanNames[0];
            if (applicationContext.isSingleton(beanName)) {
                throw new IllegalStateException(
                        "Componente " + type.getName() + " esta registrado como bean Spring com escopo singleton "
                                + "(o padrao). Componentes JTA carregam estado por requisicao, entao PRECISAM ser "
                                + "@Scope(\"prototype\") quando registrados como bean Spring - caso contrario o "
                                + "estado de uma requisicao vaza para as seguintes sob concorrencia. Adicione "
                                + "@Scope(\"prototype\") a classe " + type.getSimpleName() + ".");
            }
            return applicationContext.getBean(type);
        }

        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Nao foi possivel instanciar o componente " + type.getName()
                    + ". Componentes com construtor que recebe argumentos (para DI) precisam ser registrados "
                    + "como @Component (com @Scope(\"prototype\")) para o Spring saber como construi-los.", e);
        }
    }
}
