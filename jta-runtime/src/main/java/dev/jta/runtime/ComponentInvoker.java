package dev.jta.runtime;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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
 * <p><b>Limitacao conhecida do MVP:</b> conversao de parametros suporta
 * apenas {@code String}, {@code int}/{@code Integer}, {@code long}/
 * {@code Long}, {@code double}/{@code Double} e {@code boolean}/
 * {@code Boolean}. Tipos compostos (listas, objetos aninhados) precisam
 * ser carregados pelo proprio componente via um servico injetado, nao
 * por bind automatico de query param.
 */
public final class ComponentInvoker {

    private final ComponentFactory factory;

    public ComponentInvoker(ComponentFactory factory) {
        this.factory = factory;
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
     */
    public void populateFromParams(Object instance, Map<String, String[]> params, Set<String> bindableFields) {
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
            setField(instance, field, values[0]);
        }
    }

    /**
     * Popula campos a partir de path variables extraidas pelo framework
     * web (ver o adaptador correspondente, ex: {@code JtaRouteRegistrar}
     * em jta-spring-boot-starter). Compartilha a mesma conversao de tipo,
     * o mesmo mecanismo de "campo publico = estado", e a mesma restricao
     * a {@code bindableFields} usada para query params - a diferenca e so
     * de onde o valor vem na requisicao.
     */
    public void populateFromPathVariables(Object instance, Map<String, String> pathVariables, Set<String> bindableFields) {
        if (pathVariables == null || pathVariables.isEmpty()) {
            return;
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
            setField(instance, field, value);
        }
    }

    private void setField(Object instance, Field field, String rawValue) {
        try {
            Object converted = convert(rawValue, field.getType());
            if (converted != null) {
                field.set(instance, converted);
            }
            // tipo nao suportado (convert devolveu null): ignorado
            // silenciosamente no MVP - o componente mantem o valor default
            // do campo. Documentado como limitacao.
        } catch (IllegalAccessException | NumberFormatException e) {
            throw new IllegalArgumentException("Nao foi possivel converter '" + rawValue + "' para o campo '"
                    + field.getName() + "' (" + field.getType().getSimpleName() + ") em " + instance.getClass().getName(), e);
        }
    }

    /**
     * Coercao de {@code String} (sempre a forma em que um valor chega de
     * uma requisicao HTTP - query param, path variable, form data, ou
     * argumento posicional de acao {@code __jtaArgN}) para um dos tipos
     * simples suportados. Extraido de {@link #setField} para ser
     * reutilizado tambem por {@link #invokeAction(Object, String, String[])}
     * - as duas vias (campo de estado, argumento de acao) NUNCA devem
     * divergir silenciosamente na lista de tipos suportados.
     *
     * @return o valor convertido, ou {@code null} se {@code targetType}
     *         nao e um dos tipos suportados (chamador decide o que fazer:
     *         {@link #setField} ignora silenciosamente, {@link #invokeAction}
     *         nunca deveria chegar aqui com um tipo nao suportado porque o
     *         processor ja rejeita isso em compile-time).
     */
    private static Object convert(String rawValue, Class<?> targetType) {
        if (targetType == String.class) {
            return rawValue;
        } else if (targetType == int.class || targetType == Integer.class) {
            return Integer.parseInt(rawValue);
        } else if (targetType == long.class || targetType == Long.class) {
            return Long.parseLong(rawValue);
        } else if (targetType == double.class || targetType == Double.class) {
            return Double.parseDouble(rawValue);
        } else if (targetType == boolean.class || targetType == Boolean.class) {
            return Boolean.parseBoolean(rawValue);
        }
        return null;
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
}
