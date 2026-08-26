package dev.jta.tck;

/**
 * Uma feature JTA que o TCK sabe verificar - a lista foi levantada lendo
 * os 4 adaptadores existentes hoje (Spring, Javalin, standalone, Quarkus),
 * nao inventada a priori. Cada adaptador declara, via
 * {@link JtaAdapterHarness#supportedFeatures()}, quais destas ele
 * realmente suporta - o que ele nao declarar vira um skip nomeado e
 * visivel em {@link AbstractJtaTck}, nunca um "passa" silencioso.
 */
public enum JtaFeature {

    /** {@code @Route} + path variables - GET de pagina. */
    ROUTING("Roteamento (@Route + path variables)"),

    /** Endpoint generico de acao HTMX ({@code POST /__jta/action/{selector}}). */
    ACTIONS("Acoes HTMX (POST /__jta/action/{selector})"),

    /** Allowlist de acoes: um nome de metodo real mas nao declarado como acao deve devolver 404, nao invocar via reflection. */
    ACTION_ALLOWLIST("Allowlist de acoes (metodo nao declarado -> 404, nao invocacao arbitraria)"),

    /** {@code @RequiresRole}/{@code @AllowAnonymous} - aqui o TCK so verifica o lado "sem autenticacao -> 403". */
    ROLE_AUTHORIZATION("Autorizacao por role (@RequiresRole) - acesso nao autenticado a pagina restrita"),

    /** {@code @Sse} - conexao recebe o HTML re-renderizado do componente. */
    SSE("Server-Sent Events (@Sse)"),

    /** {@code Translations.translate}/{@code {{ 'chave' | translate }}} - lookup de i18n validado em compile-time. */
    I18N("i18n / traducao (Translations.translate)"),

    /**
     * Protecao CSRF nativa (double-submit HMAC) - ver {@code jta-runtime/csrf}.
     * Ja implementada no framework; nenhum harness a declara ainda, entao a
     * linha da matriz fica ⛔ mesmo com o probe ja escrito em
     * {@link AbstractJtaTck}.
     */
    CSRF("Protecao CSRF nativa (double-submit HMAC)"),

    /**
     * Sessao agnostica de framework (JtaSession/SessionStore). Ja
     * implementada; ainda nao declarada por nenhum harness.
     */
    SESSION("Sessao agnostica de framework"),

    /**
     * Upload de arquivo (multipart/form-data). Ja implementado; ainda nao
     * declarado por nenhum harness.
     */
    FILE_UPLOAD("Upload de arquivo (multipart/form-data)"),

    /**
     * Composicao de componentes com passagem de argumentos ({@code @Use} +
     * {@code @Input}). Ja implementada; ainda nao declarada por nenhum harness.
     */
    COMPONENT_COMPOSITION("Composicao de componentes com argumentos (@Use + @Input)");

    private final String description;

    JtaFeature(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }
}
