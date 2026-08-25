package dev.jta.runtime;

import dev.jta.core.ConversionException;
import dev.jta.core.ConverterRegistry;
import dev.jta.runtime.session.JtaSession;
import dev.jta.runtime.upload.UploadedFile;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Instancia um componente (via {@link ComponentFactory}, injetado -
 * agnostico de framework/DI), popula seus campos publicos a partir dos
 * parametros de uma requisicao (o mecanismo de "estado gerenciado pelo
 * backend": o estado atual e enviado de volta como query params/path
 * variables a cada acao HTMX ou GET de pagina, e reidratado aqui) e
 * invoca acoes/renderiza via reflection.
 *
 * <p>Extraido de {@code JtaComponentInvoker} (jta-spring-boot-starter) na
 * extracao do nucleo agnostico - a unica mudanca de comportamento e
 * {@link #instantiate}, que agora delega para {@link ComponentFactory} em
 * vez de conter a logica do {@code ApplicationContext} do Spring
 * diretamente. Todo o resto (populate/invoke/validate) e identico.
 *
 * <p><b>Conversao de tipos</b> (fase de correcao de dados): delegada a
 * {@link ConverterRegistry} (jta-core) em vez de viver embutida aqui -
 * ver essa classe para a lista completa de tipos suportados (primitivos,
 * enums, {@code LocalDate}/{@code LocalDateTime}, {@code BigDecimal},
 * {@code UUID}, {@code List<T>}/arrays, {@code Optional<T>}) e para o que
 * e validado em compile-time por {@code JtaAnnotationProcessor} contra
 * essa mesma lista. Uma falha de conversao nunca mais e ignorada
 * silenciosamente nem propaga crua: vira uma entrada no
 * {@code Map<String,String>} de erros devolvido por
 * {@link #populateFromParams}/{@link #populateFromPathVariables}, no
 * mesmo formato que {@link #validate} ja usa para violacoes de Bean
 * Validation - o chamador (ver {@code JtaActionDispatcher}/
 * {@code JtaPageDispatcher}) funde os dois e os aplica via
 * {@link #applyErrors}.
 */
public final class ComponentInvoker {

    private final ComponentFactory factory;
    private final ConverterRegistry converters;

    public ComponentInvoker(ComponentFactory factory) {
        this(factory, new ConverterRegistry());
    }

    public ComponentInvoker(ComponentFactory factory, ConverterRegistry converters) {
        this.factory = factory;
        this.converters = converters;
    }

    public Object instantiate(Class<?> type) {
        return factory.instantiate(type);
    }

    /**
     * Instancia um FILHO aninhado (composicao de componentes) e popula os
     * campos {@code @Input} indicados diretamente via reflection, SEM
     * coercao de tipo - os valores em {@code inputs} ja chegam tipados
     * (avaliados como expressao Java real no processo do PAI, nunca como
     * {@code String} vinda de uma requisicao HTTP). Um mismatch de tipo
     * aqui vira {@link IllegalArgumentException} de {@code Field.set} -
     * gap aceito e documentado nesta fase (conversao de tipos entre
     * pai/filho fica para uma frente separada), nao um sistema de
     * coercao construido as pressas.
     *
     * <p>Chamado pelo {@code .jte} gerado do PAI, nunca diretamente por
     * codigo de aplicacao - a validacao de que {@code inputs} so contem
     * chaves que o processor ja confirmou serem campos {@code @Input}
     * legitimos do filho aconteceu em compile-time; aqui, defesa em
     * profundidade extra: um campo existente mas SEM {@code @Input} e
     * ignorado silenciosamente em vez de setado, mesmo que o nome bata.
     */
    public Object instantiateChild(Class<?> type, Map<String, Object> inputs) {
        Object instance = factory.instantiate(type);
        for (Map.Entry<String, Object> entry : inputs.entrySet()) {
            setInputField(instance, entry.getKey(), entry.getValue());
        }
        callInitIfPresent(instance);
        return instance;
    }

    private void setInputField(Object instance, String name, Object value) {
        Field field;
        try {
            field = instance.getClass().getField(name);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Campo de input '" + name + "' nao encontrado em "
                    + instance.getClass().getName() + " - metadata de componente desatualizada?", e);
        }
        if (!field.isAnnotationPresent(dev.jta.core.Input.class)) {
            // defesa em profundidade: o processor ja validou isto em
            // compile-time contra o codigo-fonte real do filho; se por
            // algum motivo (jar desatualizado, etc.) o campo resolvido em
            // runtime nao e mais @Input, nao setamos - fail-closed, igual
            // ao espirito de bindableFields para requisicoes HTTP.
            return;
        }
        try {
            field.set(instance, value);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Nao foi possivel popular o campo @Input '" + name + "' em "
                    + instance.getClass().getName(), e);
        }
    }

    /**
     * Popula campos a partir de query params/form data - restrito a
     * {@code bindableFields} (ver SECURITY.md, achado #5: antes desta
     * correcao, TODO campo publico era bindavel so por ser publico,
     * independente de o template referencia-lo ou nao - mass assignment
     * classico). {@code bindableFields} vem de
     * {@code ComponentMetadata.bindableFields()}, computado pelo
     * processor em compile-time a partir do que o template realmente usa.
     *
     * <p>Campos {@code List<T>}/array recebem todos os valores enviados
     * para aquele nome (bind multi-valor); qualquer outro tipo usa o
     * ultimo valor da lista (convencao de formulario HTML - ex: o truque
     * de checkbox com um {@code <input type="hidden">} do mesmo nome
     * antes dele no DOM, onde o valor que deve prevalecer e o ultimo).
     *
     * @return mapa de erros de conversao (campo -&gt; mensagem), vazio se
     *         nada falhou - mesmo formato usado por {@link #validate},
     *         para o chamador fundir os dois antes de {@link #applyErrors}
     */
    public Map<String, String> populateFromParams(Object instance, Map<String, String[]> params, Set<String> bindableFields) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (Field field : instance.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (isReservedFieldName(field.getName()) || !bindableFields.contains(field.getName())) {
                continue;
            }
            String[] values = params.get(field.getName());
            if (values == null || values.length == 0) {
                continue;
            }
            setField(instance, field, values, errors);
        }
        return errors;
    }

    /**
     * Popula campos a partir de path variables extraidas pelo framework
     * web (ver o adaptador correspondente, ex: {@code JtaRouteRegistrar}
     * em jta-spring-boot-starter). Compartilha a mesma conversao de tipo,
     * o mesmo mecanismo de "campo publico = estado", e a mesma restricao
     * a {@code bindableFields} usada para query params - a diferenca e so
     * de onde o valor vem na requisicao (sempre um unico valor).
     *
     * @return mapa de erros de conversao, no mesmo formato de {@link #populateFromParams}
     */
    public Map<String, String> populateFromPathVariables(Object instance, Map<String, String> pathVariables, Set<String> bindableFields) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (pathVariables == null || pathVariables.isEmpty()) {
            return errors;
        }
        for (Field field : instance.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (isReservedFieldName(field.getName()) || !bindableFields.contains(field.getName())) {
                continue;
            }
            String value = pathVariables.get(field.getName());
            if (value == null) {
                continue;
            }
            setField(instance, field, new String[] {value}, errors);
        }
        return errors;
    }

    /**
     * Popula campos publicos do tipo {@link UploadedFile} a partir das
     * partes de arquivo de uma requisicao {@code multipart/form-data} -
     * mesma restricao de allowlist de {@link #populateFromParams}
     * ({@code uploadFields}, computado pelo processor em compile-time a
     * partir dos campos publicos desse tipo), so que a fonte do valor e a
     * parte de arquivo ja extraida pelo adaptador, nao uma {@code String}
     * de query/form - sem coercao de tipo nenhuma, {@code UploadedFile} e
     * atribuido diretamente.
     */
    public void populateUploads(Object instance, Map<String, UploadedFile> uploads, Set<String> uploadFields) {
        for (Field field : instance.getClass().getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            if (!uploadFields.contains(field.getName()) || !UploadedFile.class.isAssignableFrom(field.getType())) {
                continue;
            }
            UploadedFile file = uploads.get(field.getName());
            if (file == null) {
                continue;
            }
            try {
                field.set(instance, file);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Nao foi possivel popular o campo de upload '" + field.getName()
                        + "' em " + instance.getClass().getName(), e);
            }
        }
    }

    /**
     * Segunda camada de defesa contra o vetor de mass-assignment que
     * {@link dev.jta.core.ReservedFieldNames} documenta: mesmo que
     * {@code bindableFields} (calculado em compile-time) por algum bug
     * viesse a conter um desses nomes, este runtime nunca os popula a
     * partir da requisicao - defesa em profundidade, mesmo padrao do
     * achado #1 do SECURITY.md, onde a checagem primaria (compile-time,
     * ver {@code JtaAnnotationProcessor}) nao e a unica linha de defesa.
     */
    private static boolean isReservedFieldName(String name) {
        return dev.jta.core.ReservedFieldNames.ALL.contains(name);
    }

    /**
     * Converte {@code rawValues} (via {@link ConverterRegistry}, usando o
     * tipo generico do campo para resolver {@code List<T>}/{@code Optional<T>})
     * e atribui ao campo. Uma {@link ConversionException} (dado invalido
     * do usuario) vira uma entrada em {@code errorsOut} em vez de
     * propagar - falha de acesso via reflection ({@link IllegalAccessException},
     * so pode ser bug do framework, ja que o campo e sempre publico)
     * continua propagando como {@link IllegalStateException}, igual ao
     * resto desta classe.
     */
    private void setField(Object instance, Field field, String[] rawValues, Map<String, String> errorsOut) {
        Object converted;
        try {
            Class<?> type = field.getType();
            if (List.class.isAssignableFrom(type) || type.isArray()) {
                converted = converters.convertMulti(field.getGenericType(), List.of(rawValues));
            } else {
                converted = converters.convert(field.getGenericType(), rawValues[rawValues.length - 1]);
            }
        } catch (ConversionException e) {
            errorsOut.put(field.getName(), e.getMessage());
            return;
        }
        try {
            field.set(instance, converted);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Nao foi possivel popular o campo '" + field.getName() + "' em "
                    + instance.getClass().getName(), e);
        }
    }

    /**
     * Coercao de {@code String} (sempre a forma em que um valor chega de
     * uma requisicao HTTP - query param, path variable, form data, ou
     * argumento posicional de acao {@code __jtaArgN}) para o tipo alvo,
     * delegada ao mesmo {@link ConverterRegistry} que {@link #setField}
     * usa - as duas vias (campo de estado, argumento de acao) NUNCA devem
     * divergir silenciosamente na lista de tipos suportados.
     *
     * <p>Antes da fase de correcao de dados esta coercao era uma cadeia de
     * {@code if} local com uma lista de tipos propria; unificar as duas
     * vias no registry e o que garante que enums, {@code LocalDate},
     * {@code UUID} e afins - e o parse de booleano ciente de checkbox
     * ({@code "on"}) - valham igualmente para argumentos de acao.
     *
     * @throws ConversionException se o valor nao puder ser convertido ou o
     *                             tipo nao for suportado - o processor ja
     *                             rejeita tipo de parametro de acao nao
     *                             suportado em compile-time, entao aqui
     *                             isso so acontece com dado invalido do
     *                             utilizador.
     */
    private Object convert(String rawValue, Class<?> targetType) {
        return converters.convert(targetType, rawValue);
    }

    /**
     * Invoca a acao. Se o metodo lancar {@link dev.jta.core.Redirect}, a
     * excecao e desembrulhada de {@link InvocationTargetException} e
     * relancada como ela mesma - o chamador ({@link JtaActionDispatcher})
     * a captura para devolver um {@code ActionResult.Redirect} em vez de
     * renderizar o fragmento normalmente.
     *
     * <p><b>Defesa em profundidade:</b> so resolve metodos {@code void}
     * (a mesma definicao de "acao" usada pelo processor em compile-time) -
     * a checagem primaria e o chamador validar {@code actionName} contra
     * {@code ComponentMetadata.actions()} antes de sequer chamar isto, mas
     * esta classe nao deveria confiar apenas no chamador se lembrar de
     * checar (ver SECURITY.md, achado #1 - invocacao de metodo arbitrario
     * era possivel exatamente porque nada restringia a resolucao por
     * reflection ao conjunto de acoes reais).
     */
    public void invokeAction(Object instance, String actionName) {
        invokeAction(instance, actionName, new String[0]);
    }

    /**
     * Sobrecarga com argumentos posicionais (query params
     * {@code __jtaArgN}, ja extraidos e ordenados pelo chamador - ver
     * {@code JtaActionDispatcher}). Cada argumento bruto e convertido via
     * {@link #convert} para o tipo do parametro correspondente na
     * assinatura real do metodo (resolvida por posicao, nunca por nome -
     * nomes de parametro so sobrevivem no bytecode com {@code -parameters},
     * nao garantido).
     *
     * <p>Mesma defesa em profundidade de sempre: so resolve metodos
     * {@code public void} com a aridade EXATA recebida - a checagem
     * primaria (aridade declarada bate com a quantidade de
     * {@code __jtaArgN} presentes na requisicao) e do chamador, mas esta
     * classe nunca confia apenas nisso (ver SECURITY.md, achado #1).
     */
    public void invokeAction(Object instance, String actionName, String[] rawArgs) {
        Method method = findPublicVoidMethod(instance.getClass(), actionName, rawArgs.length)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Acao '" + actionName + "' (aridade " + rawArgs.length + ") nao encontrada em "
                                + instance.getClass().getName()));
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[rawArgs.length];
        for (int i = 0; i < rawArgs.length; i++) {
            args[i] = convert(rawArgs[i], paramTypes[i]);
        }
        try {
            method.invoke(instance, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof dev.jta.core.Redirect redirect) {
                throw redirect;
            }
            throw new IllegalStateException("Falha ao invocar a acao '" + actionName + "' em " + instance.getClass().getName(), e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Falha ao invocar a acao '" + actionName + "' em " + instance.getClass().getName(), e);
        }
    }

    /**
     * Chama um metodo publico {@code void init()} sem argumentos, se o
     * componente declarar um - convencao (nao interface) para carregar
     * estado derivado (ex: de um banco de dados, usando um path variable
     * ja reidratado) antes do primeiro render ou de uma acao.
     *
     * <p>E o hook de ciclo de vida que faltava: sem ele, um componente de
     * edicao (carregar um registro existente pelo {@code id} do path
     * antes de mostrar o formulario) precisaria de um workaround manual
     * misturando metodos de template com efeito colateral. Chamado tanto
     * no GET de pagina quanto antes de uma acao - deve ser idempotente
     * (seguro de chamar mais de uma vez).
     */
    public void callInitIfPresent(Object instance) {
        findPublicVoidMethod(instance.getClass(), "init", 0)
                .ifPresent(m -> {
                    try {
                        m.invoke(instance);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new IllegalStateException("Falha ao invocar init() em " + instance.getClass().getName(), e);
                    }
                });
    }

    /**
     * Resolve um metodo publico, {@code void}, sem argumentos, pelo nome -
     * a mesma definicao de "acao" que {@code JtaAnnotationProcessor} usa
     * em compile-time para popular {@code ComponentMetadata.actions()}.
     * Restringir a resolucao a este formato aqui (em vez de aceitar
     * qualquer metodo publico de qualquer retorno) e a segunda camada de
     * defesa contra invocacao de metodo arbitrario - a primeira e o
     * chamador nunca invocar {@link #invokeAction} com um nome que nao
     * esteja em {@code metadata.actions()}.
     */
    private java.util.Optional<Method> findPublicVoidMethod(Class<?> type, String name, int arity) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arity && method.getReturnType() == void.class) {
                return java.util.Optional.of(method);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * Valida o componente contra as constraints Jakarta Validation
     * (ex: {@code @NotBlank}, {@code @Email}, {@code @Size}) declaradas
     * diretamente nos campos publicos - decisao de design do projeto:
     * usar o padrao Jakarta em vez de anotacoes proprias.
     *
     * <p>Retorna um mapa vazio se nao houver violacoes (ou se nenhum
     * {@link Validator} estiver disponivel - o chamador so passa um
     * quando o bean/instancia existe, tornando a validacao totalmente
     * opcional: um projeto sem Bean Validation no classpath simplesmente
     * nunca aciona nada aqui).
     *
     * <p><b>Limitacao conhecida do MVP:</b> so valida os campos do proprio
     * componente (sem {@code @Valid} em cascata para objetos aninhados),
     * e a chave do mapa de erros e o nome simples do campo (sem suporte a
     * caminhos aninhados tipo {@code endereco.cep}).
     */
    public Map<String, String> validate(Object instance, Validator validator) {
        Set<ConstraintViolation<Object>> violations = validator.validate(instance);
        if (violations.isEmpty()) {
            return Map.of();
        }
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<Object> violation : violations) {
            errors.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return errors;
    }

    /**
     * Popula um campo publico opcional chamado {@code errors} (tipo
     * {@code Map<String,String>}) com o resultado de {@link #validate},
     * para o template poder mostrar {@code {{ errors.campo }}}. Se o
     * componente nao declarar esse campo, esta chamada e um no-op
     * silencioso - o gate de validacao (a acao nao roda com erros)
     * funciona independente de o dev querer exibir as mensagens ou nao.
     */
    public void applyErrors(Object instance, Map<String, String> errors) {
        for (Field field : instance.getClass().getFields()) {
            if (field.getName().equals("errors") && Map.class.isAssignableFrom(field.getType())) {
                try {
                    field.set(instance, errors);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Nao foi possivel popular o campo 'errors' em " + instance.getClass().getName(), e);
                }
                return;
            }
        }
        // sem campo 'errors' declarado - ok, e opcional.
    }

    /**
     * Popula um campo publico opcional chamado {@code session} (tipo
     * {@link JtaSession}) com a sessao resolvida pelo adaptador para a
     * requisicao atual - mesmo padrao exato de {@link #applyErrors}: se o
     * componente nao declarar esse campo, e um no-op silencioso. O campo
     * {@code session} nunca e bindavel via query params/form data (ver
     * {@code populateFromParams}/{@code populateFromPathVariables}, que so
     * populam campos em {@code bindableFields} - um template raramente
     * referencia {@code session} via {{ }}, e mesmo que referenciasse,
     * {@link #setField} nao sabe converter uma {@code String} crua para
     * {@link JtaSession}, entao um valor de request nunca sobrescreveria a
     * sessao real resolvida pelo runtime).
     */
    public void applySession(Object instance, JtaSession session) {
        for (Field field : instance.getClass().getFields()) {
            if (field.getName().equals("session") && JtaSession.class.isAssignableFrom(field.getType())) {
                try {
                    field.set(instance, session);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Nao foi possivel popular o campo 'session' em " + instance.getClass().getName(), e);
                }
                return;
            }
        }
        // sem campo 'session' declarado - ok, e opcional.
    }

    /**
     * Popula os campos publicos opcionais {@code flashSuccess}/
     * {@code flashError} (ver {@link dev.jta.core.ReservedFieldNames}) com
     * a mensagem de uso unico consumida da sessao pelo chamador ({@code
     * JtaPageDispatcher}) - mesmo padrao de no-op silencioso de
     * {@link #applySession}/{@link #applyErrors} para quem nao declarar o
     * campo. {@code null} e um valor valido (nenhuma flash pendente).
     */
    public void applyFlash(Object instance, String flashSuccess, String flashError) {
        setStringFieldIfPresent(instance, "flashSuccess", flashSuccess);
        setStringFieldIfPresent(instance, "flashError", flashError);
    }

    /**
     * Popula os campos publicos opcionais {@code errorStatus}/
     * {@code errorPath}/{@code errorDetail} (ver
     * {@link dev.jta.core.ReservedFieldNames}) de um componente
     * {@code @ErrorPage} - ver {@code JtaErrorPageRenderer}. Mesmo padrao
     * de no-op silencioso: um componente de erro so precisa declarar os
     * campos que o template realmente usa.
     */
    public void applyErrorInfo(Object instance, int errorStatus, String errorPath, String errorDetail) {
        setStringFieldIfPresent(instance, "errorPath", errorPath);
        setStringFieldIfPresent(instance, "errorDetail", errorDetail);
        for (Field field : instance.getClass().getFields()) {
            if (field.getName().equals("errorStatus") && (field.getType() == int.class || field.getType() == Integer.class)) {
                try {
                    field.set(instance, errorStatus);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Nao foi possivel popular o campo 'errorStatus' em " + instance.getClass().getName(), e);
                }
                return;
            }
        }
    }

    private void setStringFieldIfPresent(Object instance, String name, String value) {
        for (Field field : instance.getClass().getFields()) {
            if (field.getName().equals(name) && field.getType() == String.class) {
                try {
                    field.set(instance, value);
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException("Nao foi possivel popular o campo '" + name + "' em " + instance.getClass().getName(), e);
                }
                return;
            }
        }
    }
}
