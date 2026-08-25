package dev.jta.runtime.session;

/**
 * Representacao minima e agnostica de framework de uma sessao HTTP, o
 * suficiente para um componente JTA guardar/ler estado entre requisicoes
 * (ex: usuario logado, carrinho, wizard multi-passo) sem o dev precisar
 * saber qual mecanismo de sessao o host por baixo usa.
 *
 * <p>Cada adaptador resolve/traduz sua propria nocao de sessao para esta
 * interface - ver {@code ServletJtaSession} (Spring, sobre
 * {@code HttpSession}), o wrapper equivalente em Javalin (sobre a sessao
 * Jetty), e {@code InMemorySessionStore} (jta-runtime) para hosts sem
 * sessao de servidor nenhuma por baixo (ex: jta-standalone).
 *
 * <p>Populada no componente pelo mesmo padrao de {@code applyErrors}: um
 * campo publico opcional chamado {@code session} (ver
 * {@code ComponentInvoker#applySession}) - se o componente nao declarar
 * esse campo, e um no-op silencioso.
 */
public interface JtaSession {

    /** Identificador estavel da sessao (usado como valor da cookie pelo adaptador). */
    String id();

    Object attribute(String name);

    void setAttribute(String name, Object value);

    void removeAttribute(String name);

    /** Invalida a sessao - implementacoes devem tornar {@link #attribute} vazio e liberar recursos associados. */
    void invalidate();

    /** {@link JtaSession} default para hosts sem nenhuma nocao de sessao configurada. */
    static JtaSession none() {
        return NoopJtaSession.INSTANCE;
    }
}
