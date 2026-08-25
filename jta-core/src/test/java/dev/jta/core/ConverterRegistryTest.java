package dev.jta.core;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConverterRegistryTest {

    private final ConverterRegistry registry = new ConverterRegistry();

    enum Status { ATIVO, INATIVO }

    // --- tipos primitivos/wrapper (herdados do ComponentInvoker original) ---

    @Test
    void convertsString() {
        assertEquals("ola", registry.convert(String.class, "ola"));
    }

    @Test
    void convertsIntAndInteger() {
        assertEquals(42, registry.convert(int.class, "42"));
        assertEquals(42, registry.convert(Integer.class, "42"));
    }

    @Test
    void convertsLongAndLong() {
        assertEquals(42L, registry.convert(long.class, "42"));
        assertEquals(42L, registry.convert(Long.class, "42"));
    }

    @Test
    void convertsDoubleAndDouble() {
        assertEquals(3.14, registry.convert(double.class, "3.14"));
        assertEquals(3.14, registry.convert(Double.class, "3.14"));
    }

    @Test
    void convertsBooleanAndBoolean() {
        assertEquals(true, registry.convert(boolean.class, "true"));
        assertEquals(true, registry.convert(Boolean.class, "true"));
    }

    // --- novos tipos ---

    @Test
    void convertsEnumByConstantName() {
        assertEquals(Status.ATIVO, registry.convert(Status.class, "ATIVO"));
    }

    @Test
    void enumConversionIsCaseSensitive() {
        assertThrows(ConversionException.class, () -> registry.convert(Status.class, "ativo"));
    }

    @Test
    void enumConversionRejectsUnknownConstant() {
        assertThrows(ConversionException.class, () -> registry.convert(Status.class, "BANIDO"));
    }

    @Test
    void convertsLocalDateIso() {
        assertEquals(LocalDate.of(2026, 8, 25), registry.convert(LocalDate.class, "2026-08-25"));
    }

    @Test
    void convertsLocalDateTimeIso() {
        assertEquals(LocalDateTime.of(2026, 8, 25, 10, 30), registry.convert(LocalDateTime.class, "2026-08-25T10:30"));
    }

    @Test
    void convertsBigDecimal() {
        assertEquals(new BigDecimal("19.99"), registry.convert(BigDecimal.class, "19.99"));
    }

    @Test
    void convertsUuid() {
        UUID id = UUID.randomUUID();
        assertEquals(id, registry.convert(UUID.class, id.toString()));
    }

    // --- List<T> / Optional<T> (via reflexao, tipo generico de um campo real) ---

    private java.lang.reflect.Type genericTypeOf(String fieldName) throws NoSuchFieldException {
        return Holder.class.getField(fieldName).getGenericType();
    }

    static final class Holder {
        public List<Integer> tags;
        public Optional<String> nickname;
        public Optional<Integer> age;
    }

    @Test
    void convertsListOfSupportedElementType() throws Exception {
        Object result = registry.convertMulti(genericTypeOf("tags"), List.of("1", "2", "3"));
        assertEquals(List.of(1, 2, 3), result);
    }

    @Test
    void listConversionFailsIfAnyElementIsInvalid() throws Exception {
        assertThrows(ConversionException.class,
                () -> registry.convertMulti(genericTypeOf("tags"), List.of("1", "abc")));
    }

    @Test
    void optionalOfPresentValueDelegatesToInnerConverter() throws Exception {
        assertEquals(Optional.of("bob"), registry.convert(genericTypeOf("nickname"), "bob"));
        assertEquals(Optional.of(30), registry.convert(genericTypeOf("age"), "30"));
    }

    @Test
    void optionalOfEmptyOrNullStringIsEmpty() throws Exception {
        assertEquals(Optional.empty(), registry.convert(genericTypeOf("nickname"), ""));
        assertEquals(Optional.empty(), registry.convert(genericTypeOf("nickname"), null));
    }

    // --- multi-valor com campo de valor unico: ultimo valor vence ---

    @Test
    void convertMultiOnSingleValuedFieldKeepsLastValue() {
        assertEquals(2, registry.convertMulti(int.class, List.of("1", "2")));
    }

    // --- falha ---

    @Test
    void unsupportedTypeThrowsConversionException() {
        assertThrows(ConversionException.class, () -> registry.convert(Object.class, "x"));
    }

    @Test
    void malformedValueThrowsConversionExceptionNotNumberFormatException() {
        ConversionException e = assertThrows(ConversionException.class, () -> registry.convert(int.class, "abc"));
        assertTrue(e.getMessage().contains("abc"));
    }

    // --- extensibilidade ---

    record Money(BigDecimal amount) {
    }

    @Test
    void supportsCustomRegisteredConverter() {
        assertFalse(registry.supportsSimple(Money.class));
        registry.register(Money.class, s -> new Money(new BigDecimal(s)));
        assertTrue(registry.supportsSimple(Money.class));
        assertEquals(new Money(new BigDecimal("10.50")), registry.convert(Money.class, "10.50"));
    }
}
