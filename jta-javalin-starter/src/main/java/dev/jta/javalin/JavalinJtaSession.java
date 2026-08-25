package dev.jta.javalin;

import dev.jta.runtime.session.JtaSession;
import jakarta.servlet.http.HttpSession;

/**
 * {@link JtaSession} do adaptador Javalin: delega para o {@link HttpSession}
 * do Jetty embutido ({@code ctx.req().getSession(true)}) - mesma logica de
 * {@code ServletJtaSession} no starter Spring (ambos expoem
 * {@code jakarta.servlet.http.HttpServletRequest} por baixo), duplicada
 * aqui porque os dois modulos nao compartilham classes internas entre si.
 */
final class JavalinJtaSession implements JtaSession {

    private final HttpSession httpSession;

    JavalinJtaSession(HttpSession httpSession) {
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
