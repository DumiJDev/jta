package dev.jta.runtime;

import dev.jta.core.Redirect;
import dev.jta.core.ReservedFieldNames;
import dev.jta.runtime.session.JtaSession;

/**
 * Guarda/consome a mensagem flash de uso unico de um {@link Redirect} na
 * {@link JtaSession} - ponte entre {@link JtaActionDispatcher} (que a
 * escreve, quando a acao lanca {@link Redirect#withFlashSuccess}/
 * {@link Redirect#withFlashError}) e {@link JtaPageDispatcher} (que a le E
 * remove na proxima pagina renderizada, tornando-a "de uso unico" - uma
 * flash nao sobrevive a um segundo GET/refresh).
 *
 * <p>As chaves de sessao usadas aqui sao internas deste pacote, distintas
 * dos nomes de campo reservados em {@link ReservedFieldNames} (que sao o
 * namespace de CAMPOS PUBLICOS de componente, nunca de atributos de
 * sessao) - so para deixar claro que nao ha colisao possivel entre os
 * dois namespaces.
 */
final class FlashSupport {

    private static final String SESSION_KEY_SUCCESS = "__jta_flashSuccess";
    private static final String SESSION_KEY_ERROR = "__jta_flashError";

    private FlashSupport() {
    }

    static void store(JtaSession session, Redirect redirect) {
        if (redirect.flashSuccess() != null) {
            session.setAttribute(SESSION_KEY_SUCCESS, redirect.flashSuccess());
        }
        if (redirect.flashError() != null) {
            session.setAttribute(SESSION_KEY_ERROR, redirect.flashError());
        }
    }

    record Values(String success, String error) {
    }

    /** Le e remove (uso unico) a flash pendente na sessao, se houver. */
    static Values consume(JtaSession session) {
        Object success = session.attribute(SESSION_KEY_SUCCESS);
        Object error = session.attribute(SESSION_KEY_ERROR);
        if (success != null) {
            session.removeAttribute(SESSION_KEY_SUCCESS);
        }
        if (error != null) {
            session.removeAttribute(SESSION_KEY_ERROR);
        }
        return new Values(success == null ? null : success.toString(), error == null ? null : error.toString());
    }
}
