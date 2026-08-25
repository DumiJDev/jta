package dev.jta.runtime.session;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link SessionStore} zero-dependencia (so {@code java.util.concurrent}/
 * {@code java.time}) para hosts sem sessao de servidor por baixo - ex:
 * {@code jta-standalone}, que roda sobre {@code com.sun.net.httpserver}.
 *
 * <p>Expiracao por sliding-window: cada leitura ({@link #get}/{@link #getOrCreate})
 * renova o prazo de expiracao em {@code ttl} a partir de agora - checada
 * preguicosamente (nenhuma thread de limpeza em background), entao uma
 * sessao expirada so e removida do mapa na proxima vez que alguem tentar
 * acessa-la por aquele id.
 */
public final class InMemorySessionStore implements SessionStore {

    private final Duration ttl;
    private final Map<String, Entry> sessions = new ConcurrentHashMap<>();

    public InMemorySessionStore(Duration ttl) {
        this.ttl = ttl;
    }

    @Override
    public JtaSession getOrCreate(String sessionId) {
        if (sessionId != null) {
            Entry entry = sessions.get(sessionId);
            if (entry != null && !entry.isExpired()) {
                entry.touch();
                return entry.session;
            }
            sessions.remove(sessionId);
        }
        String newId = UUID.randomUUID().toString();
        Entry entry = new Entry(new InMemoryJtaSession(newId, this));
        sessions.put(newId, entry);
        return entry.session;
    }

    @Override
    public Optional<JtaSession> get(String sessionId) {
        if (sessionId == null) {
            return Optional.empty();
        }
        Entry entry = sessions.get(sessionId);
        if (entry == null || entry.isExpired()) {
            sessions.remove(sessionId);
            return Optional.empty();
        }
        entry.touch();
        return Optional.of(entry.session);
    }

    @Override
    public void invalidate(String sessionId) {
        if (sessionId != null) {
            sessions.remove(sessionId);
        }
    }

    private final class Entry {
        private final InMemoryJtaSession session;
        private volatile Instant expiresAt;

        Entry(InMemoryJtaSession session) {
            this.session = session;
            touch();
        }

        void touch() {
            expiresAt = Instant.now().plus(ttl);
        }

        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private static final class InMemoryJtaSession implements JtaSession {
        private final String id;
        private final InMemorySessionStore owner;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();

        InMemoryJtaSession(String id, InMemorySessionStore owner) {
            this.id = id;
            this.owner = owner;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public Object attribute(String name) {
            return attributes.get(name);
        }

        @Override
        public void setAttribute(String name, Object value) {
            if (value == null) {
                attributes.remove(name);
            } else {
                attributes.put(name, value);
            }
        }

        @Override
        public void removeAttribute(String name) {
            attributes.remove(name);
        }

        @Override
        public void invalidate() {
            owner.invalidate(id);
            attributes.clear();
        }
    }
}
