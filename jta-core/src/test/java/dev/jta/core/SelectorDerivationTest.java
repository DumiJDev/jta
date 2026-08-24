package dev.jta.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectorDerivationTest {

    @Test
    void derivesKebabCaseFromPackageAndClass() {
        assertEquals("acme-ui-components-user-card",
                SelectorDerivation.derive("com.acme.ui.components.UserCard"));
    }

    @Test
    void stripsCommonDomainPrefixByDefault() {
        assertTrue(SelectorDerivation.derive("com.acme.Button").startsWith("acme-"));
        assertTrue(SelectorDerivation.derive("org.acme.Button").startsWith("acme-"));
    }

    @Test
    void keepsDomainPrefixWhenDisabled() {
        assertEquals("com-acme-button",
                SelectorDerivation.derive("com.acme.Button", false, "-"));
    }

    @Test
    void twoDifferentFqnsNeverCollide() {
        String a = SelectorDerivation.derive("com.acme.widgets.Button");
        String b = SelectorDerivation.derive("com.other.widgets.Button");
        assertTrue(!a.equals(b), "selectors derived from different FQNs must never collide: " + a + " vs " + b);
    }

    @Test
    void singleSegmentClassGetsPrefixedToStayValidAsCustomElement() {
        // classe no pacote default (raro, mas nao pode gerar selector sem hifen)
        String selector = SelectorDerivation.derive("Button", true, "-");
        assertTrue(selector.contains("-"), "selector deve conter hifen para ser um custom element valido: " + selector);
    }

    @Test
    void respectsCustomSeparator() {
        assertEquals("acme.ui.button",
                SelectorDerivation.derive("com.acme.ui.Button", true, "."));
    }
}
