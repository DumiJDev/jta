package dev.jta.runtime.session;

import java.util.Optional;

/**
 * Resolve/cria {@link JtaSession} a partir de um id de sessao (tipicamente
 * o valor de uma cookie). Implementacao default zero-dependencia:
 * {@link InMemorySessionStore} - hosts com container proprio (Spring
 * Servlet, Jetty/Javalin) normalmente preferem a sessao nativa do container
 * em vez desta SPI (ver {@code ServletJtaSession} no starter Spring), mas
 * ela continua disponivel para qualquer host que queira uma sessao
 * simples em memoria.
 */
public interface SessionStore {

    /** Devolve a sessao existente para {@code sessionId}, ou cria uma nova (com novo id) se {@code sessionId} for {@code null}/desconhecido/expirado. */
    JtaSession getOrCreate(String sessionId);

    /** Devolve a sessao existente para {@code sessionId}, se ainda valida - nunca cria uma nova. */
    Optional<JtaSession> get(String sessionId);

    void invalidate(String sessionId);
}
