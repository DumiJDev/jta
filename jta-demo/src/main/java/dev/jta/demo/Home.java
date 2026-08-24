package dev.jta.demo;

import dev.jta.core.AComponent;
import dev.jta.core.Route;

@Route(value = "/", layout = SiteLayout.class)
@AComponent(
    template = "<main class=\"jta-container\">"
             + "<h1>JTA - demo</h1>"
             + "<div class=\"jta-card\"><a href=\"/contador\">Contador</a><br/>estado simples, sem DI</div>"
             + "<div class=\"jta-card\"><a href=\"/tutores\">Tutores &amp; pets</a><br/>persistencia real (JPA + H2), relacoes um-para-muitos, injecao de servico Spring</div>"
             + "<div class=\"jta-card\"><a href=\"/veterinarios\">Veterinarios</a><br/>cadastro/edicao protegidos por RequiresRole(ADMIN) - entre como admin/admin</div>"
             + "<div class=\"jta-card\"><a href=\"/tarefas\">Tarefas</a><br/>condicional e laco (if/for) e toggle de estado boolean</div>"
             + "<div class=\"jta-card\"><a href=\"/contato\">Fale conosco</a><br/>Jakarta Validation bloqueando a acao</div>"
             + "</main>"
)
public class Home {
}
