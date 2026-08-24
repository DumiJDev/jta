package dev.jta.demo;

import dev.jta.core.AComponent;
import dev.jta.core.AllowAnonymous;
import dev.jta.core.Route;

@Route(value = "/", layout = SiteLayout.class)
@AllowAnonymous
@AComponent(
    // Sem '@' literal em lugar nenhum do texto (nem em nomes de anotacao,
    // nem na URL do CDN sem versao pinada) - o processor trata qualquer
    // '@' como inicio de diretiva JTE, mesmo dentro de um bloco @raw.
    template = "<main class=\"jta-container\">"
             + "<div class=\"app-hero\">"
             + "<h1>Gestao Escolar</h1>"
             + "<p>Alunos, professores, turmas, disciplinas, matriculas e notas - tudo em componentes JTA "
             + "tipados sobre JTE + HTMX, sem uma linha de JavaScript proprio.</p>"
             + "</div>"

             + "<div class=\"jta-card\">"
             + "<span class=\"app-live-stat\">"
             + "<span class=\"app-live-dot\"></span>"
             + "<span hx-ext=\"sse\" sse-connect=\"/sse/matriculas\" sse-swap=\"message\">"
             + "<span id=\"contador-matriculas\">carregando...</span></span>"
             + "</span>"
             + "<br/>atualizado ao vivo a cada 3s via Server-Sent Events (Sse)"
             + "</div>"

             + "<div class=\"app-grid\">"
             + "<div class=\"jta-card\"><a href=\"/turmas\">Turmas &amp; disciplinas</a>"
             + "<span>catalogo publico - AllowAnonymous, sem login</span></div>"
             + "<div class=\"jta-card\"><a href=\"/alunos\">Alunos</a>"
             + "<span>persistencia real (JPA + H2), busca ao vivo via HTMX, protegido por RequiresRole ADMIN</span></div>"
             + "<div class=\"jta-card\"><a href=\"/professores\">Professores</a>"
             + "<span>cadastro protegido por RequiresRole ADMIN</span></div>"
             + "<div class=\"jta-card\">Lancamento de notas"
             + "<span>a partir da ficha de um aluno - protegido por RequiresRole PROFESSOR, uma role diferente de ADMIN</span></div>"
             + "</div>"

             + "<script src=\"https://unpkg.com/htmx-ext-sse\"></script>"
             + "</main>"
)
public class Home {
}
