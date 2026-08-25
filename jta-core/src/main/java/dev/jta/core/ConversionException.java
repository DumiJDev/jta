package dev.jta.core;

/**
 * Lancada por {@link ConverterRegistry} quando um valor bruto (sempre
 * {@code String}, vindo de query param/form data/path variable) nao pode
 * ser convertido para o tipo alvo declarado no campo do componente.
 *
 * <p>Excecao de dado do usuario, nao de bug do framework - por isso
 * {@code unchecked} mas com uma mensagem pensada para acabar (via
 * {@code ComponentInvoker}) num {@code Map<String,String>} de erros de
 * formulario, no mesmo formato que violacoes de Bean Validation ja usam
 * (ver {@code ComponentInvoker#validate}), nunca como um 500 cru.
 */
public class ConversionException extends RuntimeException {

    public ConversionException(String message) {
        super(message);
    }

    public ConversionException(String message, Throwable cause) {
        super(message, cause);
    }
}
