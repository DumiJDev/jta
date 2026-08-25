package dev.jta.spring;

import dev.jta.runtime.session.JtaSession;
import jakarta.servlet.http.HttpSession;

/**
 * {@link JtaSession} do adaptador Spring: delega diretamente para
 * {@link HttpSession} do container servlet - o Spring/Tomcat ja resolvem
 * criacao, cookie ({@code JSESSIONID} por padrao) e expiracao, entao nao
 * ha motivo para reinventar isso com {@code InMemorySessionStore}
 * (jta-runtime), que existe para hosts sem sessao de servidor nenhuma por
 * baixo (ex: jta-standalone).
 */
final class ServletJtaSession implements JtaSession {

    private final HttpSession httpSession;

    ServletJtaSession(HttpSession httpSession) {
        this.httpSession = httpSession;
    }

    @Override
    public String id() {
        return httpSession.getId();
    }

    @Override
    public Object attribute(String name) {
        return httpSession.getAttribute(name);
    }

    @Override
    public void setAttribute(String name, Object value) {
        httpSession.setAttribute(name, value);
    }

    @Override
    public void removeAttribute(String name) {
        httpSession.removeAttribute(name);
    }

    @Override
    public void invalidate() {
        httpSession.invalidate();
    }
}
