package dev.jta.core;

import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Registro central de conversao {@code String -> T}, usado por
 * {@code ComponentInvoker} (jta-runtime) para reidratar campos publicos
 * de um componente a partir de query params/form data/path variables.
 *
 * <p>Sucessora da conversao que vivia embutida (e duplicada, e limitada a
 * 5 tipos) dentro de {@code ComponentInvoker#setField} - agora um registro
 * extensivel: alem dos tipos primitivos originais, suporta enums (por
 * nome, case-sensitive - {@link Enum#valueOf}), {@link LocalDate}/
 * {@link LocalDateTime} (formato ISO-8601, via {@code parse(CharSequence)}),
 * {@link BigDecimal}, {@link UUID}, {@code List<T>}/arrays (bind
 * multi-valor, delegando a conversao de cada elemento para o conversor de
 * {@code T}) e {@code Optional<T>} (string vazia/ausente vira
 * {@link Optional#empty()}, delegando o resto para o conversor de
 * {@code T}).
 *
 * <p><b>Fonte de verdade compartilhada com compile-time:</b>
 * {@link #SUPPORTED_SIMPLE_TYPE_NAMES} e a lista canonica dos tipos
 * "folha" (nao-parametrizados, nao-enum) que este registro sabe converter
 * - {@code JtaAnnotationProcessor} (jta-processor) referencia esta mesma
 * constante para rejeitar em compile-time um campo bindavel de tipo nao
 * suportado, em vez de deixar isso falhar silenciosamente (ou so em
 * runtime) como no MVP anterior. Ao adicionar um conversor aqui, adicione
 * o nome totalmente qualificado (ou o nome primitivo, ex: {@code "int"})
 * a essa constante tambem.
 *
 * <p><b>Extensivel:</b> {@link #register} permite que um consumidor
 * registre um conversor proprio para um tipo adicional (ex: um value
 * object do dominio) - a checagem de compile-time do processor, porem,
 * so conhece os tipos built-in listados em
 * {@link #SUPPORTED_SIMPLE_TYPE_NAMES}, entao um tipo customizado
 * registrado so em runtime nao passa por essa validacao de compile-time
 * (documentado como limitacao: extensao de compile-time exigiria o
 * processor conhecer o registro de conversores do modulo consumidor, o
 * que nao e o caso hoje).
 */
public final class ConverterRegistry {

    /**
     * Nomes (FQN para tipos de referencia, nome primitivo para
     * primitivos) dos tipos "folha" suportados nativamente - mantenha em
     * sincronia com os {@link #register} feitos no construtor default.
     * Enums (qualquer tipo cujo {@code TypeElement.getKind() == ENUM}) e
     * os wrappers {@code List<T>}/{@code T[]}/{@code Optional<T>} sao
     * suportados estruturalmente, nao por nome, entao nao aparecem aqui.
     */
    public static final Set<String> SUPPORTED_SIMPLE_TYPE_NAMES = Set.of(
            "java.lang.String",
            "int", "java.lang.Integer",
            "long", "java.lang.Long",
            "double", "java.lang.Double",
            "boolean", "java.lang.Boolean",
            "java.math.BigDecimal",
            "java.util.UUID",
            "java.time.LocalDate",
            "java.time.LocalDateTime"
    );

    private final Map<Class<?>, Function<String, ?>> converters = new ConcurrentHashMap<>();

    public ConverterRegistry() {
        register(String.class, s -> s);
        register(int.class, Integer::parseInt);
        register(Integer.class, Integer::parseInt);
        register(long.class, Long::parseLong);
        register(Long.class, Long::parseLong);
        register(double.class, Double::parseDouble);
        register(Double.class, Double::parseDouble);
        register(boolean.class, Boolean::parseBoolean);
        register(Boolean.class, Boolean::parseBoolean);
        register(BigDecimal.class, BigDecimal::new);
        register(UUID.class, UUID::fromString);
        register(LocalDate.class, LocalDate::parse);
        register(LocalDateTime.class, LocalDateTime::parse);
    }

    /**
     * Registra (ou substitui) o conversor para {@code type} - ponto de
     * extensao para um consumidor que precise bindar um tipo alem dos
     * built-in (ver limitacao de compile-time na javadoc da classe).
     */
    public <T> void register(Class<T> type, Function<String, T> converter) {
        converters.put(type, converter);
    }

    /** {@code true} se ha um conversor registrado diretamente para {@code type} (nao considera enum/List/Optional). */
    public boolean supportsSimple(Class<?> type) {
        return converters.containsKey(type);
    }

    /**
     * Converte um unico valor bruto para {@code targetType}, que pode ser
     * um {@link Class} simples, um enum, ou um {@link ParameterizedType}
     * {@code Optional<T>} (uma {@code List<T>} de um so valor tambem e
     * aceita aqui, virando uma lista de um elemento).
     *
     * @throws ConversionException se o valor nao puder ser convertido, ou
     *                              se {@code targetType} nao for suportado
     */
    public Object convert(Type targetType, String rawValue) {
        return convertSingle(targetType, rawValue);
    }

    /**
     * Converte multiplos valores brutos (ex: varios query params com o
     * mesmo nome) para {@code targetType}. Se {@code targetType} for
     * {@code List<T>} ou um array {@code T[]}, cada valor e convertido
     * individualmente para {@code T} e agregado; caso contrario (campo de
     * valor unico), usa a semantica convencional de formularios HTML de
     * "o ultimo valor declarado vence" (ex: o truque de checkbox com um
     * {@code <input type="hidden">} do mesmo nome antes dele no DOM).
     *
     * @throws ConversionException se algum valor nao puder ser convertido
     */
    public Object convertMulti(Type targetType, List<String> rawValues) {
        Class<?> rawClass = rawClassOf(targetType);

        if (rawClass != null && rawClass.isArray()) {
            Class<?> componentType = rawClass.getComponentType();
            Object array = Array.newInstance(componentType, rawValues.size());
            for (int i = 0; i < rawValues.size(); i++) {
                Array.set(array, i, convertSingle(componentType, rawValues.get(i)));
            }
            return array;
        }

        if (rawClass == List.class) {
            Type elementType = parameterOf(targetType, List.class);
            List<Object> result = new ArrayList<>(rawValues.size());
            for (String raw : rawValues) {
                result.add(convertSingle(elementType, raw));
            }
            return List.copyOf(result);
        }

        String lastValue = rawValues.isEmpty() ? null : rawValues.get(rawValues.size() - 1);
        return convertSingle(targetType, lastValue);
    }

    private Object convertSingle(Type type, String rawValue) {
        Class<?> rawClass = rawClassOf(type);
        if (rawClass == null) {
            throw new ConversionException("Tipo nao suportado para conversao: " + type);
        }

        if (rawClass == Optional.class) {
            Type innerType = parameterOf(type, Optional.class);
            if (rawValue == null || rawValue.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(convertSingle(innerType, rawValue));
        }

        if (rawClass == List.class) {
            // valor unico bindado num campo List<T>: uma lista de um elemento so.
            Type elementType = parameterOf(type, List.class);
            return rawValue == null ? List.of() : List.of(convertSingle(elementType, rawValue));
        }

        if (rawClass.isEnum()) {
            return convertEnum(rawClass, rawValue);
        }

        Function<String, ?> converter = converters.get(rawClass);
        if (converter == null) {
            throw new ConversionException("Tipo nao suportado para conversao: " + rawClass.getName());
        }
        try {
            return converter.apply(rawValue);
        } catch (ConversionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ConversionException(
                    "Nao foi possivel converter '" + rawValue + "' para " + rawClass.getSimpleName(), e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object convertEnum(Class<?> enumClass, String rawValue) {
        try {
            return Enum.valueOf((Class<Enum>) enumClass, rawValue);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ConversionException(
                    "Valor '" + rawValue + "' nao e uma constante valida de " + enumClass.getSimpleName(), e);
        }
    }

    private static Class<?> rawClassOf(Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        return null;
    }

    private static Type parameterOf(Type type, Class<?> expectedRawType) {
        if (type instanceof ParameterizedType parameterized && parameterized.getRawType() == expectedRawType) {
            return parameterized.getActualTypeArguments()[0];
        }
        throw new ConversionException(
                expectedRawType.getSimpleName() + " precisa declarar seu tipo generico (ex: "
                        + expectedRawType.getSimpleName() + "<String>) para ser bindavel: " + type);
    }
}
