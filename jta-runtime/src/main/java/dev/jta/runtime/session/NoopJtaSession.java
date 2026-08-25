package dev.jta.runtime.session;

/** {@link JtaSession} default para hosts sem nenhuma nocao de sessao - no-op total. */
final class NoopJtaSession implements JtaSession {

    static final NoopJtaSession INSTANCE = new NoopJtaSession();

    private NoopJtaSession() {
    }

    @Override
    public String id() {
        return null;
    }

    @Override
    public Object attribute(String name) {
        return null;
    }

    @Override
    public void setAttribute(String name, Object value) {
        // no-op - sem sessao nenhuma por baixo, nao ha onde guardar.
    }

    @Override
    public void removeAttribute(String name) {
        // no-op
    }

    @Override
    public void invalidate() {
        // no-op
    }
}
