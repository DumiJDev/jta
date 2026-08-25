package dev.jta.runtime;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cobre a fase de correcao de dados: bind multi-valor (query param com
 * varios valores -&gt; campo {@code List<T>}) e falha de conversao virando
 * uma entrada no {@code Map<String,String>} de erros (mesmo formato de
 * {@link ComponentInvoker#validate}) em vez de propagar uma excecao crua -
 * ver {@link JtaActionDispatcher}/{@link JtaPageDispatcher} para onde esse
 * mapa e fundido com erros de Bean Validation e aplicado via
 * {@link ComponentInvoker#applyErrors}.
 */
class ComponentInvokerTest {

    private final ComponentInvoker invoker = new ComponentInvoker(new ReflectionComponentFactory());

    public static class Sample {
        public String name;
        public int age;
        public List<Integer> tags;
        public boolean active;
        public Map<String, String> errors = Map.of();
    }

    @Test
    void populatesSingleValuedFieldsFromParams() {
        Sample instance = new Sample();
        Map<String, String[]> params = Map.of(
                "name", new String[] {"Ana"},
                "age", new String[] {"30"});

        Map<String, String> errors = invoker.populateFromParams(instance, params, Set.of("name", "age"));

        assertTrue(errors.isEmpty());
        assertEquals("Ana", instance.name);
        assertEquals(30, instance.age);
    }

    @Test
    void multiValuedParamBindsToListField() {
        Sample instance = new Sample();
        Map<String, String[]> params = Map.of("tags", new String[] {"1", "2", "3"});

        Map<String, String> errors = invoker.populateFromParams(instance, params, Set.of("tags"));

        assertTrue(errors.isEmpty());
        assertEquals(List.of(1, 2, 3), instance.tags);
    }

    @Test
    void singleValuedFieldUsesLastValueWhenMultipleValuesSubmitted() {
        // convencao HTML: <input type="hidden" name="active" value="false"/>
        // seguido do checkbox real - o navegador manda os dois quando
        // marcado, nessa ordem, e o ultimo (o real) deve prevalecer.
        Sample instance = new Sample();
        Map<String, String[]> params = Map.of("active", new String[] {"false", "true"});

        Map<String, String> errors = invoker.populateFromParams(instance, params, Set.of("active"));

        assertTrue(errors.isEmpty());
        assertTrue(instance.active);
    }

    @Test
    void conversionFailureBecomesFormErrorInsteadOfException() {
        Sample instance = new Sample();
        Map<String, String[]> params = Map.of("age", new String[] {"not-a-number"});

        Map<String, String> errors = invoker.populateFromParams(instance, params, Set.of("age"));

        assertEquals(1, errors.size());
        assertTrue(errors.containsKey("age"));
        assertEquals(0, instance.age, "valor default deve ser preservado - nada foi atribuido ao campo");
    }

    @Test
    void multiValueConversionFailureIsReportedOnce() {
        Sample instance = new Sample();
        Map<String, String[]> params = Map.of("tags", new String[] {"1", "not-a-number"});

        Map<String, String> errors = invoker.populateFromParams(instance, params, Set.of("tags"));

        assertEquals(1, errors.size());
        assertTrue(errors.containsKey("tags"));
    }

    @Test
    void fieldsNotInBindableFieldsAreIgnored() {
        Sample instance = new Sample();
        Map<String, String[]> params = Map.of("name", new String[] {"Ana"});

        Map<String, String> errors = invoker.populateFromParams(instance, params, Set.of());

        assertTrue(errors.isEmpty());
        assertNull(instance.name);
    }

    @Test
    void populatesFromPathVariablesAndReportsConversionErrors() {
        Sample instance = new Sample();

        Map<String, String> errors = invoker.populateFromPathVariables(instance, Map.of("age", "xyz"), Set.of("age"));

        assertEquals(1, errors.size());
        assertTrue(errors.containsKey("age"));
    }

    @Test
    void conversionErrorsCanBeAppliedToOptionalErrorsFieldLikeValidationErrors() {
        Sample instance = new Sample();
        Map<String, String> conversionErrors = invoker.populateFromParams(
                instance, Map.of("age", new String[] {"abc"}), Set.of("age"));

        invoker.applyErrors(instance, conversionErrors);

        assertEquals("age", instance.errors.keySet().iterator().next());
    }
}
