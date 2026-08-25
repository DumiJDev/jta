package dev.jta.core;

import java.util.Locale;

/**
 * Resolve o {@link Locale} de uma requisicao a partir do header HTTP
 * {@code Accept-Language} bruto (ou {@code null}/vazio se a requisicao
 * nao enviou nenhum).
 *
 * <p>O contrato recebe a string do header (nao um tipo de request
 * completo) deliberadamente: e o unico dado de negociacao de locale que
 * praticamente todo framework host (Spring MVC, Javalin, Vert.x/Quarkus,
 * um servidor HTTP puro) expoe de forma trivial e identica - amarrar
 * este contrato a, por exemplo, {@code HttpServletRequest} acoplaria
 * jta-core (zero-dependencias, ver pom.xml) a um framework especifico.
 *
 * <p>Implementado por {@link AcceptLanguageLocaleResolver} (o default,
 * usado quando um adaptador nao configura nenhum) - um consumidor pode
 * fornecer sua propria implementacao (ex: locale fixo por tenant, ou lido
 * de uma preferencia de usuario persistida) e passa-la ao construtor de
 * {@code JtaActionDispatcher}/{@code JtaPageDispatcher}.
 */
@FunctionalInterface
public interface LocaleResolver {

    Locale resolve(String acceptLanguageHeader);

    /** Resolver default: {@link AcceptLanguageLocaleResolver} caindo em {@link Locale#getDefault()} quando o header estiver ausente/vazio/invalido. */
    static LocaleResolver acceptLanguageOrDefault() {
        return new AcceptLanguageLocaleResolver(Locale.getDefault());
    }
}
