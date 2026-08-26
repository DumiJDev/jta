package dev.jta.quarkus.tck;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

/**
 * Fixture minimo de i18n ({@code {{ 'chave' | translate }}}) - usado pelo
 * TCK ({@code QuarkusJtaTckTest}) para provar que
 * {@code dev.jta.core.Translations} funciona sem nenhum codigo especifico
 * de adaptador (a chave e validada em compile-time contra
 * {@code src/test/resources/messages.properties}).
 */
@Route("/saudacao")
@AComponent(template = "<p>{{ 'saudacao' | translate }}</p>")
public class Saudacao {
}
