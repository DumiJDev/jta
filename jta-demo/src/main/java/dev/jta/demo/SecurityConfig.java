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
 * {@code @RequiresRole}/{@code @AllowAnonymous} de verdade no demo -
 * antes desta mudanca, {@code jta-demo} nem tinha Spring Security como
 * dependencia, entao a aplicacao em runtime de {@code JtaSecurityEnforcer}
 * nunca tinha sido exercitada num app rodando de verdade (so em
 * compile-time por {@code scripts/smoke-test.sh}).
 *
 * <p><b>Autorizacao de URL vs autorizacao do JTA:</b>
 * {@code authorizeHttpRequests(auth -> auth.anyRequest().permitAll())}
 * deliberadamente nao bloqueia nada por URL - a autorizacao de pagina e
 * feita pelo proprio JTA via {@code @RequiresRole}
 * ({@code JtaSecurityEnforcer}/{@code SecurityEnforcer}), nao pelas regras
 * do Spring Security. O papel do Spring Security aqui e so fornecer o
 * mecanismo de autenticacao (login) para {@code SecurityContextHolder}
 * ter alguem autenticado quando o usuario escolher entrar - a maioria das
 * paginas do demo continua acessivel sem login, de proposito, isolando a
 * demonstracao de seguranca so ao cadastro/edicao de veterinario.
 *
 * <p><b>CSRF (ver SECURITY.md, achado #6):</b> o endpoint de acao do JTA
 * ({@code /__jta/action/**}) e um {@code POST} puro sem token CSRF - o
 * proprio SECURITY.md ja previa que adicionar Spring Security com sessao
 * bloquearia essas acoes com 403 (falha fechado, nao um buraco) a menos
 * que o CSRF seja isento explicitamente para esse caminho. E exatamente
 * o que fazemos aqui - documentado, nao um descuido.
 *
 * <p><b>HTTP Basic + form login juntos:</b> form login e o fluxo pensado
 * para o navegador (link "Entrar" no nav); HTTP Basic fica ligado tambem
 * so para {@code VeterinarioSecurityTest} poder autenticar via
 * {@code TestRestTemplate.withBasicAuth(...)} sem lidar com a pagina de
 * login gerada automaticamente.
 *
 * <p><b>Usuarios em memoria - so para o demo:</b> {@code admin/admin}
 * (role {@code ADMIN}) e {@code user/user} (role {@code USER}). Isto e
 * deliberadamente inadequado para producao (senhas triviais, sem
 * persistencia) - um app de verdade usaria um
 * {@code UserDetailsService} contra um banco real. Usamos
 * {@link BCryptPasswordEncoder} explicito (nao o atalho depreciado
 * {@code User.withDefaultPasswordEncoder()}) porque este e um demo de
 * referencia - nao deveria ensinar um encoder inseguro nem um metodo
 * marcado como nao-recomendado pelo proprio Spring Security.
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
        var user = User.withUsername("user").password(encoder.encode("user")).roles("USER").build();
        return new InMemoryUserDetailsManager(admin, user);
    }
}
