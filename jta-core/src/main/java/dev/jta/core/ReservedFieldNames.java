package dev.jta.core;

import java.util.Set;

/**
 * Nomes de campo publico reservados para uso interno futuro do runtime
 * (ex: sessao, flash messages, detalhe de erro) - nunca bindaveis a
 * partir de uma requisicao, mesmo que um componente os declare e o
 * template os interpole.
 *
 * <p><b>Por que isto existe antes de qualquer uma dessas features:</b> a
 * auditoria que motivou esta lista encontrou um vetor de mass-assignment
 * que uma feature de flash messages criaria, ainda nao implementada. Se
 * um campo {@code public String flashSuccess} for interpolado no
 * template ({@code {{ flashSuccess }}}), o calculo de
 * {@code bindableFields} inclui automaticamente qualquer campo referenciado
 * no template (e {@code String} e um dos tipos que
 * {@code ComponentInvoker.setField} converte sem restricao) - um pedido
 * {@code ?flashSuccess=mensagem-forjada} substituiria a mensagem real por
 * conteudo controlado pelo cliente. O JTE escapa por padrao, entao isto
 * nao e XSS, mas e spoofing de UI (ver SECURITY.md, achado #5, da mesma
 * familia de mass assignment).
 *
 * <p>Definir a lista agora, antes das features que a vao popular
 * (sessao/flash/erro), evita que cada uma nasca com o mesmo buraco e
 * precise da sua propria correcao depois. Consumida em duas camadas de
 * defesa em profundidade, mesmo padrao do achado #1 do SECURITY.md:
 * {@code JtaAnnotationProcessor} exclui estes nomes de
 * {@code bindableFields} em compile-time, e {@code ComponentInvoker}
 * ignora-os incondicionalmente em runtime, para que um bug futuro no
 * processor nao reabra sozinho o buraco.
 */
public final class ReservedFieldNames {

    /**
     * Reservado para o campo de sessao ({@code public JtaSession session})
     * que o runtime vai injetar quando a feature de sessao existir.
     */
    public static final String SESSION = "session";

    /**
     * Ja em uso hoje pelo mecanismo de validacao Jakarta
     * ({@code ComponentInvoker.applyErrors}) - listado aqui para que a
     * lista seja a fonte unica de verdade de "nomes que o runtime, nao o
     * cliente, decide", nao so os que ainda vao nascer.
     */
    public static final String ERRORS = "errors";

    /** Reservados para a feature de flash messages (ver javadoc da classe). */
    public static final String FLASH_SUCCESS = "flashSuccess";
    public static final String FLASH_ERROR = "flashError";

    /** Reservados para a feature de paginas de erro como componente. */
    public static final String ERROR_STATUS = "errorStatus";
    public static final String ERROR_PATH = "errorPath";
    public static final String ERROR_DETAIL = "errorDetail";

    public static final Set<String> ALL = Set.of(
            SESSION, ERRORS, FLASH_SUCCESS, FLASH_ERROR, ERROR_STATUS, ERROR_PATH, ERROR_DETAIL);

    private ReservedFieldNames() {
    }
}
