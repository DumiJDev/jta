package dev.jta.quarkus;

import dev.jta.runtime.ComponentFactory;
import io.quarkus.arc.Arc;
import io.quarkus.arc.InstanceHandle;
import jakarta.enterprise.context.Dependent;

/**
 * {@link ComponentFactory} do adaptador Quarkus: se o dev registrou a
 * classe como bean CDI (Arc), reusa o container; senao cai para um
 * construtor sem argumentos.
 *
 * <p><b>Diferenca importante em relacao ao adaptador Spring:</b> no CDI/Arc,
 * uma classe so vira bean se declarar explicitamente uma anotacao de escopo
 * (ex: {@code @Dependent}, {@code @ApplicationScoped}) - um POJO sem
 * nenhuma anotacao simplesmente NAO e um bean, e {@link Arc#container()}
 * devolve "indisponivel" para ele (cai no fallback de reflection abaixo).
 * Isso significa que o bug classico do Spring (estado de requisicao
 * vazando porque {@code @Component} sozinho ja implica singleton) nao
 * pode acontecer por acidente aqui - mas se o dev quiser DI via
 * construtor, precisa optar explicitamente por {@code @Dependent}
 * (nunca {@code @ApplicationScoped}/{@code @Singleton}, que travam este
 * factory pelo mesmo motivo do {@code SpringComponentFactory}: componentes
 * JTA carregam estado por requisicao).
 */
final class QuarkusComponentFactory implements ComponentFactory {

    @Override
    public Object instantiate(Class<?> type) {
        InstanceHandle<?> handle = Arc.container().instance(type);
        if (handle.isAvailable()) {
            if (handle.getBean().getScope() != Dependent.class) {
                throw new IllegalStateException(
                        "Componente " + type.getName() + " esta registrado como bean CDI com escopo "
                                + handle.getBean().getScope().getSimpleName() + " (nao @Dependent). Componentes JTA "
                                + "carregam estado por requisicao, entao PRECISAM ser @Dependent quando registrados "
                                + "como bean CDI - caso contrario o estado de uma requisicao vaza para as "
                                + "seguintes sob concorrencia. Troque para @Dependent em " + type.getSimpleName() + ".");
            }
            return handle.get();
        }

        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Nao foi possivel instanciar o componente " + type.getName()
                    + " via construtor sem argumentos. Componentes com construtor que recebe argumentos (para DI) "
                    + "precisam ser anotados com @Dependent para o Quarkus saber como construi-los.", e);
        }
    }
}
