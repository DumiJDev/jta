package dev.jta.runtime;

import dev.jta.core.ConversionException;
import dev.jta.core.ConverterRegistry;
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
            if (!bindableFields.contains(field.getName())) {
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
            if (!bindableFields.contains(field.getName())) {
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
        Method method = findPublicVoidNoArgMethod(instance.getClass(), actionName)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Acao '" + actionName + "' nao encontrada em " + instance.getClass().getName()));
        try {
            method.invoke(instance);
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
        findPublicVoidNoArgMethod(instance.getClass(), "init")
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
    private java.util.Optional<Method> findPublicVoidNoArgMethod(Class<?> type, String name) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == 0 && method.getReturnType() == void.class) {
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
}
