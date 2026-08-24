package dev.jta.spring;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Instancia um componente, popula seus campos publicos a partir dos
 * parametros de uma requisicao (o mecanismo de "estado gerenciado pelo
 * backend": o estado atual e enviado de volta como query params/path
 * variables a cada acao HTMX ou GET de pagina, e reidratado aqui) e
 * invoca acoes/renderiza via reflection.
 *
 * <p><b>Limitacao conhecida do MVP:</b> conversao de parametros suporta
 * apenas {@code String}, {@code int}/{@code Integer}, {@code long}/
 * {@code Long}, {@code double}/{@code Double} e {@code boolean}/
 * {@code Boolean}. Tipos compostos (listas, objetos aninhados) precisam
 * ser carregados pelo proprio componente via um servico injetado, nao
 * por bind automatico de query param.
 *
 * <p>Registrado como bean explicitamente por {@link JtaAutoConfiguration}
 * (nao via {@code @Component}), ja que este pacote fica fora do
 * component-scan padrao do app consumidor.
 */
class JtaComponentInvoker {

    private final ApplicationContext applicationContext;

    JtaComponentInvoker(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * Instancia o componente. Se o dev registrou a classe como bean Spring
     * (para receber DI de servicos via construtor), reusa a infra do
     * Spring; senao cai para um construtor sem argumentos simples.
     *
     * <p><b>Trava de correcao importante:</b> um componente JTA carrega
     * estado por requisicao (campos publicos populados a cada chamada).
     * Se o dev registrar a classe como {@code @Component} sem
     * {@code @Scope("prototype")}, o Spring devolve a MESMA instancia
     * singleton em toda requisicao - o estado de uma requisicao vazaria
     * para a proxima (bug de concorrencia silencioso e serio, o pior tipo
     * de bug para deixar passar batido). Por isso falhamos alto e cedo em
     * vez de deixar o dev descobrir isso sob carga em producao.
     */
    Object instantiate(Class<?> type) {
        String[] beanNames = applicationContext.getBeanNamesForType(type);
        if (beanNames.length > 0) {
            String beanName = beanNames[0];
            if (applicationContext.isSingleton(beanName)) {
                throw new IllegalStateException(
                        "Componente " + type.getName() + " esta registrado como bean Spring com escopo singleton "
                                + "(o padrao). Componentes JTA carregam estado por requisicao, entao PRECISAM ser "
                                + "@Scope(\"prototype\") quando registrados como bean Spring - caso contrario o "
                                + "estado de uma requisicao vaza para as seguintes sob concorrencia. Adicione "
                                + "@Scope(\"prototype\") a classe " + type.getSimpleName() + ".");
            }
            return applicationContext.getBean(type);
        }

        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Nao foi possivel instanciar o componente " + type.getName()
                    + ". Componentes com construtor que recebe argumentos (para DI) precisam ser registrados "
                    + "como @Component (com @Scope(\"prototype\")) para o Spring saber como construi-los.", e);
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
    void populateFromParams(Object instance, Map<String, String[]> params, java.util.Set<String> bindableFields) {
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
     * Popula campos a partir de path variables extraidas pelo Spring
     * (ver {@code HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE} em
     * {@link JtaRouteRegistrar}). Compartilha a mesma conversao de tipo,
     * o mesmo mecanismo de "campo publico = estado", e a mesma restricao
     * a {@code bindableFields} usada para query params - a diferenca e so
     * de onde o valor vem na requisicao.
     */
    void populateFromPathVariables(Object instance, Map<String, String> pathVariables, java.util.Set<String> bindableFields) {
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
            Class<?> type = field.getType();
            if (type == String.class) {
                field.set(instance, rawValue);
            } else if (type == int.class || type == Integer.class) {
                field.set(instance, Integer.parseInt(rawValue));
            } else if (type == long.class || type == Long.class) {
                field.set(instance, Long.parseLong(rawValue));
            } else if (type == double.class || type == Double.class) {
                field.set(instance, Double.parseDouble(rawValue));
            } else if (type == boolean.class || type == Boolean.class) {
                field.set(instance, Boolean.parseBoolean(rawValue));
            }
            // outros tipos: ignorado silenciosamente no MVP - o componente
            // mantem o valor default do campo. Documentado como limitacao.
        } catch (IllegalAccessException | NumberFormatException e) {
            throw new IllegalArgumentException("Nao foi possivel converter '" + rawValue + "' para o campo '"
                    + field.getName() + "' (" + field.getType().getSimpleName() + ") em " + instance.getClass().getName(), e);
        }
    }

    /**
     * Invoca a acao. Se o metodo lancar {@link dev.jta.core.Redirect}, a
     * excecao e desembrulhada de {@link InvocationTargetException} e
     * relancada como ela mesma - o chamador ({@code JtaActionController})
     * a captura para responder com {@code HX-Redirect} em vez de
     * renderizar o fragmento normalmente.
     *
     * <p><b>Defesa em profundidade:</b> so resolve metodos {@code void}
     * (a mesma definicao de "acao" usada pelo processor em compile-time) -
     * a checagem primaria e {@code JtaActionController} validar
     * {@code actionName} contra {@code ComponentMetadata.actions()} antes
     * de sequer chamar isto, mas esta classe nao deveria confiar apenas
     * no chamador se lembrar de checar (ver SECURITY.md, achado #1 -
     * invocacao de metodo arbitrario era possivel exatamente porque nada
     * restringia a resolucao por reflection ao conjunto de acoes reais).
     */
    void invokeAction(Object instance, String actionName) {
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
    void callInitIfPresent(Object instance) {
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
     * defesa contra invocacao de metodo arbitrario - a primeira e
     * {@code JtaActionController} nunca chamar {@link #invokeAction} com
     * um nome que nao esteja em {@code metadata.actions()}.
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
     * {@link Validator} estiver disponivel - ver
     * {@link JtaActionController}, que so chama isto quando o bean existe,
     * tornando a validacao totalmente opcional: um projeto sem
     * {@code spring-boot-starter-validation} no classpath simplesmente
     * nunca aciona nada aqui).
     *
     * <p><b>Limitacao conhecida do MVP:</b> so valida os campos do proprio
     * componente (sem {@code @Valid} em cascata para objetos aninhados),
     * e a chave do mapa de erros e o nome simples do campo (sem suporte a
     * caminhos aninhados tipo {@code endereco.cep}).
     */
    Map<String, String> validate(Object instance, Validator validator) {
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
    void applyErrors(Object instance, Map<String, String> errors) {
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
