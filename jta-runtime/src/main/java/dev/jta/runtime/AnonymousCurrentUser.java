package dev.jta.runtime;

import java.util.Set;

/** {@link CurrentUser} default para hosts sem nenhuma nocao de autenticacao. */
final class AnonymousCurrentUser implements CurrentUser {

    static final AnonymousCurrentUser INSTANCE = new AnonymousCurrentUser();

    private AnonymousCurrentUser() {
    }

    @Override
    public boolean isAuthenticated() {
        return false;
    }

    @Override
    public Set<String> authorities() {
        return Set.of();
    }
}
