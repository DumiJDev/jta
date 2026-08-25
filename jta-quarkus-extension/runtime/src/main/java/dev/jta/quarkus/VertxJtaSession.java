package dev.jta.quarkus;

import dev.jta.runtime.session.JtaSession;
import io.vertx.ext.web.Session;

/**
 * {@link JtaSession} do adaptador Quarkus: delega para {@link Session}
 * (Vert.x Web) - instalado via {@code SessionHandler}/{@code LocalSessionStore}
 * registrado pelo modulo deployment ({@code JtaProcessor}, ANTES dos
 * handlers de pagina/acao), a mesma dependencia ja transitiva de
 * {@code quarkus-vertx-http}.
 */
final class VertxJtaSession implements JtaSession {

    private final Session session;

    VertxJtaSession(Session session) {
        this.session = session;
    }

    @Override
    public String id() {
        return session.id();
    }

    @Override
    public Object attribute(String name) {
        return session.get(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        session.put(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        session.remove(name);
    }

    @Override
    public void invalidate() {
        session.destroy();
    }
}
