package dev.jta.spring;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * App Spring Boot minima usada so por {@link SpringJtaTckTest} - descobre
 * {@link Contador}/{@link AdminPage}/{@link Placar}/{@link Saudacao} via
 * component-scan do proprio pacote (nao precisam de {@code @Component},
 * ver javadoc de {@link Contador}) e {@link JtaAutoConfiguration} via
 * {@code META-INF/spring/...AutoConfiguration.imports} (auto-discovery
 * padrao do Spring Boot, igual a qualquer app real que so declare a
 * dependencia do starter).
 */
@SpringBootApplication
public class TckSpringApplication {
}
