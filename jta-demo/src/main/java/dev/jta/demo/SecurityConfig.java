package dev.jta.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Configuracao minima de autenticacao para dogfoodar
 * {@code @RequiresRole}/{@code @AllowAnonymous} de verdade no demo, com
 * duas roles distintas (ADMIN e PROFESSOR - nao so "logado ou nao").
 *
 * <p><b>Autorizacao de URL vs autorizacao do JTA:</b>
 * {@code authorizeHttpRequests(auth -> auth.anyRequest().permitAll())}
 * deliberadamente nao bloqueia nada por URL - a autorizacao de pagina e
 * feita pelo proprio JTA via {@code @RequiresRole}
 * ({@code SecurityEnforcer}, jta-runtime), nao pelas regras do Spring
 * Security. O papel do Spring Security aqui e so fornecer o mecanismo de
 * autenticacao (login) para o {@code SecurityContextHolder} ter alguem
 * autenticado quando o usuario escolher entrar.
 *
 * <p><b>CSRF (ver SECURITY.md, achado #6):</b> o endpoint de acao do JTA
 * ({@code /__jta/action/**}) e um {@code POST} puro sem token CSRF -
 * isento explicitamente para nao ser bloqueado com 403 (falha fechado, nao
 * um buraco - documentado, nao um descuido).
 *
 * <p><b>HTTP Basic + form login juntos:</b> form login e o fluxo pensado
 * para o navegador (link "Entrar" no nav); HTTP Basic fica ligado tambem
 * so para os testes de seguranca poderem autenticar via
 * {@code TestRestTemplate.withBasicAuth(...)} sem lidar com a pagina de
 * login gerada automaticamente.
 *
 * <p><b>Usuarios em memoria - so para o demo:</b> {@code admin/admin}
 * (role {@code ADMIN}, gestao de alunos/turmas/professores/disciplinas) e
 * {@code professor/professor} (role {@code PROFESSOR}, so pode lancar
 * notas). Deliberadamente inadequado para producao (senhas triviais, sem
 * persistencia) - um app de verdade usaria um {@code UserDetailsService}
 * contra um banco real.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.ignoringRequestMatchers("/__jta/action/**"))
                // /h2-console (profile "dev") usa iframes - sem isto, o
                // X-Frame-Options padrao do Spring Security (DENY) impede
                // o console de renderizar dentro de si mesmo.
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .formLogin(Customizer.withDefaults())
                .httpBasic(Customizer.withDefaults())
                .logout(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(PasswordEncoder encoder) {
        var admin = User.withUsername("admin").password(encoder.encode("admin")).roles("ADMIN").build();
        var professor = User.withUsername("professor").password(encoder.encode("professor")).roles("PROFESSOR").build();
        return new InMemoryUserDetailsManager(admin, professor);
    }
}
