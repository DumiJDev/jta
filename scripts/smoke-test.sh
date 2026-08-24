#!/usr/bin/env bash
# Smoke test que NAO depende de Maven nem de rede - compila jta-core e
# jta-processor com javac puro (nenhum dos dois tem dependencias externas
# por design) e roda o pipeline completo contra um componente de exemplo.
#
# Isso e o que foi realmente executado e validado durante a implementacao
# inicial. `mvn test`/`mvn verify` cobrem mais (Spring, JTE, os outros
# modulos) mas exigem acesso ao Maven Central.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="/tmp/jta-smoke-build"
rm -rf "$BUILD"
mkdir -p "$BUILD/core-classes" "$BUILD/processor-classes/META-INF/services"

echo "==> Compilando jta-core (zero dependencias)"
find "$ROOT/jta-core/src/main/java" -name "*.java" > "$BUILD/core-sources.txt"
javac -d "$BUILD/core-classes" @"$BUILD/core-sources.txt"

echo "==> Rodando SmokeTest (SelectorDerivation + JSON round-trip de ComponentMetadata)"
javac -cp "$BUILD/core-classes" -d "$BUILD/core-classes" "$ROOT/scripts/SmokeTest.java"
java -cp "$BUILD/core-classes" SmokeTest

echo "==> Compilando jta-processor (so depende de jta-core)"
find "$ROOT/jta-processor/src/main/java" -name "*.java" > "$BUILD/processor-sources.txt"
javac -cp "$BUILD/core-classes" -d "$BUILD/processor-classes" @"$BUILD/processor-sources.txt"
cp "$ROOT/jta-processor/src/main/resources/META-INF/services/javax.annotation.processing.Processor" \
   "$BUILD/processor-classes/META-INF/services/"

echo "==> Rodando o processor contra o componente Contador (caminho feliz)"
mkdir -p "$BUILD/e2e-happy/classes/jta-templates/dev/jta/demo" "$BUILD/e2e-happy/sources"
# simula o que o Maven ja teria feito antes de compilar: copiar os
# recursos externos (templateUrl/styleUrl) para target/classes
cp "$ROOT/jta-demo/src/main/resources/jta-templates/dev/jta/demo/SiteLayout.jta" \
   "$BUILD/e2e-happy/classes/jta-templates/dev/jta/demo/SiteLayout.jta"
cp "$ROOT/jta-demo/src/main/resources/jta-templates/dev/jta/demo/Contador.css" \
   "$BUILD/e2e-happy/classes/jta-templates/dev/jta/demo/Contador.css"
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-happy/classes" -s "$BUILD/e2e-happy/sources" \
      "$ROOT/jta-demo/src/main/java/dev/jta/demo/Contador.java" \
      "$ROOT/jta-demo/src/main/java/dev/jta/demo/SiteLayout.java"

echo "---- .jte gerado ----"
cat "$BUILD/e2e-happy/sources/jta-templates/dev/jta/demo/Contador.jte"
echo "---- components.json gerado ----"
cat "$BUILD/e2e-happy/classes/META-INF/jta/components.json"
echo "---- reflect-config.json gerado (GraalVM native-image) ----"
cat "$BUILD/e2e-happy/classes/META-INF/native-image/dev.jta/jta-generated/reflect-config.json"
if ! grep -q '"name": "dev.jta.demo.Contador"' "$BUILD/e2e-happy/classes/META-INF/native-image/dev.jta/jta-generated/reflect-config.json"; then
    echo "ERRO: reflect-config.json nao foi gerado corretamente!"
    exit 1
fi
echo "OK, reflect-config.json gerado para GraalVM native-image."

echo
echo "==> Rodando o processor contra um componente com bindings invalidos (deve falhar o build)"
mkdir -p "$BUILD/e2e-broken/src/dev/jta/demo2" "$BUILD/e2e-broken/classes" "$BUILD/e2e-broken/sources"
cat > "$BUILD/e2e-broken/src/dev/jta/demo2/Quebrado.java" << 'EOF'
package dev.jta.demo2;
import dev.jta.core.AComponent;
@AComponent(template = "<div>{{ titlo }}</div><button (click)=\"incrementarr()\">+</button>")
public class Quebrado {
    public String titulo = "x";
    public void incrementar() {}
}
EOF
if javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-broken/classes" -s "$BUILD/e2e-broken/sources" \
      "$BUILD/e2e-broken/src/dev/jta/demo2/Quebrado.java" 2>"$BUILD/broken-output.txt"; then
    echo "ERRO: o build deveria ter falhado para o componente com bindings invalidos!"
    exit 1
else
    echo "OK, build falhou como esperado. Mensagens de erro:"
    cat "$BUILD/broken-output.txt"
fi

echo
echo
echo "==> Testando validacao de path params (@Route com {id})"
mkdir -p "$BUILD/e2e-route/src/dev/jta/demo3" "$BUILD/e2e-route/classes" "$BUILD/e2e-route/sources"
cat > "$BUILD/e2e-route/src/dev/jta/demo3/ProdutoOk.java" << 'EOF'
package dev.jta.demo3;
import dev.jta.core.AComponent;
import dev.jta.core.Route;
@Route("/produtos/{id}")
@AComponent(template = "<div>{{ id }}</div>")
public class ProdutoOk {
    public String id;
}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-route/classes" -s "$BUILD/e2e-route/sources" \
      "$BUILD/e2e-route/src/dev/jta/demo3/ProdutoOk.java"
echo "OK: path param 'id' validado contra o campo publico 'id'"

cat > "$BUILD/e2e-route/src/dev/jta/demo3/ProdutoQuebrado.java" << 'EOF'
package dev.jta.demo3;
import dev.jta.core.AComponent;
import dev.jta.core.Route;
@Route("/produtos/{codigo}")
@AComponent(template = "<div>{{ id }}</div>")
public class ProdutoQuebrado {
    public String id;
}
EOF
if javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-route/classes" -s "$BUILD/e2e-route/sources" \
      "$BUILD/e2e-route/src/dev/jta/demo3/ProdutoQuebrado.java" 2>"$BUILD/route-broken-output.txt"; then
    echo "ERRO: o build deveria ter falhado - {codigo} nao corresponde a nenhum campo publico!"
    exit 1
else
    echo "OK, build falhou como esperado (path param sem campo correspondente):"
    cat "$BUILD/route-broken-output.txt"
fi

echo
echo "==> Testando leitura de jta.config.toml pelo processor ([selector] overrides)"
mkdir -p "$BUILD/e2e-config/src/dev/jta/demo5" "$BUILD/e2e-config/classes" "$BUILD/e2e-config/sources"
# simula o que o Maven ja teria feito antes de compilar: copiar
# src/main/resources/jta.config.toml para target/classes/jta.config.toml
cat > "$BUILD/e2e-config/classes/jta.config.toml" << 'EOF'
[selector]
strip_domain_prefix = false
separator = "."
EOF
cat > "$BUILD/e2e-config/src/dev/jta/demo5/Botao.java" << 'EOF'
package dev.jta.demo5;
import dev.jta.core.AComponent;
@AComponent(template = "<button>ok</button>")
public class Botao {}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-config/classes" -s "$BUILD/e2e-config/sources" \
      "$BUILD/e2e-config/src/dev/jta/demo5/Botao.java"
GERADO=$(grep -o '"selector": "[^"]*"' "$BUILD/e2e-config/classes/META-INF/jta/components.json")
echo "Selector gerado: $GERADO"
if [ "$GERADO" != '"selector": "dev.jta.demo5.botao"' ]; then
    echo "ERRO: esperava 'dev.jta.demo5.botao' (com separador '.' e prefixo 'dev' preservado), veio $GERADO"
    exit 1
fi
echo "OK, jta.config.toml aplicado corretamente na derivacao do seletor."

echo
echo "==> Testando templateUrl() (template externo lido de resources)"
mkdir -p "$BUILD/e2e-templateurl/src/dev/jta/demo6" "$BUILD/e2e-templateurl/classes/jta-templates/dev/jta/demo6" "$BUILD/e2e-templateurl/sources"
# simula o que o Maven ja teria feito antes de compilar: copiar
# src/main/resources/jta-templates/dev/jta/demo6/Saudacao.jta para
# target/classes/jta-templates/dev/jta/demo6/Saudacao.jta
cat > "$BUILD/e2e-templateurl/classes/jta-templates/dev/jta/demo6/Saudacao.jta" << 'EOF'
<div><h1>Ola, {{ nome }}!</h1></div>
EOF
cat > "$BUILD/e2e-templateurl/src/dev/jta/demo6/Saudacao.java" << 'EOF'
package dev.jta.demo6;
import dev.jta.core.AComponent;
@AComponent(templateUrl = "Saudacao.jta")
public class Saudacao {
    public String nome = "mundo";
}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-templateurl/classes" -s "$BUILD/e2e-templateurl/sources" \
      "$BUILD/e2e-templateurl/src/dev/jta/demo6/Saudacao.java"
echo "--- .jte gerado a partir do templateUrl ---"
cat "$BUILD/e2e-templateurl/sources/jta-templates/dev/jta/demo6/Saudacao.jte"
if ! grep -q '\${self.nome}' "$BUILD/e2e-templateurl/sources/jta-templates/dev/jta/demo6/Saudacao.jte"; then
    echo "ERRO: templateUrl nao foi transformado corretamente!"
    exit 1
fi
echo "OK, templateUrl() lido e transformado corretamente."

echo
echo "==> Testando deteccao de '@' literal em texto comum (bug real: Home.java quebrava o JTE)"
mkdir -p "$BUILD/e2e-atsign/src/dev/jta/demo8" "$BUILD/e2e-atsign/classes" "$BUILD/e2e-atsign/sources"

# caminho feliz: @for/@endfor legitimos nao devem disparar falso positivo
cat > "$BUILD/e2e-atsign/src/dev/jta/demo8/ListaOk.java" << 'EOF'
package dev.jta.demo8;
import dev.jta.core.AComponent;
@AComponent(template = "<ul>@for(var x : self.itens)<li>${x}</li>@endfor</ul>")
public class ListaOk {
    public java.util.List<String> itens = java.util.List.of("a", "b");
}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-atsign/classes" -s "$BUILD/e2e-atsign/sources" \
      "$BUILD/e2e-atsign/src/dev/jta/demo8/ListaOk.java"
echo "OK: @for/@endfor legitimos nao disparam falso positivo"

# caminho quebrado: '@' literal em texto comum (o bug real do Home.java)
cat > "$BUILD/e2e-atsign/src/dev/jta/demo8/HomeQuebrado.java" << 'EOF'
package dev.jta.demo8;
import dev.jta.core.AComponent;
@AComponent(template = "<p>Fale com a gente: contato@exemplo.com</p>")
public class HomeQuebrado {}
EOF
if javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-atsign/classes" -s "$BUILD/e2e-atsign/sources" \
      "$BUILD/e2e-atsign/src/dev/jta/demo8/HomeQuebrado.java" 2>"$BUILD/atsign-broken-output.txt"; then
    echo "ERRO: o build deveria ter falhado - '@' literal em contato@exemplo.com!"
    exit 1
else
    echo "OK, build falhou como esperado (deteccao em compile-time, nao mais um erro confuso do JTE em runtime):"
    cat "$BUILD/atsign-broken-output.txt"
fi

echo
echo "==> Testando styleUrl() (CSS externo lido de resources)"
mkdir -p "$BUILD/e2e-styleurl/src/dev/jta/demo9" "$BUILD/e2e-styleurl/classes/jta-templates/dev/jta/demo9" "$BUILD/e2e-styleurl/sources"
cat > "$BUILD/e2e-styleurl/classes/jta-templates/dev/jta/demo9/Cartao.css" << 'EOF'
h1 { color: green; }
EOF
cat > "$BUILD/e2e-styleurl/src/dev/jta/demo9/Cartao.java" << 'EOF'
package dev.jta.demo9;
import dev.jta.core.AComponent;
@AComponent(template = "<div><h1>{{ titulo }}</h1></div>", styleUrl = "Cartao.css")
public class Cartao {
    public String titulo = "ola";
}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-styleurl/classes" -s "$BUILD/e2e-styleurl/sources" \
      "$BUILD/e2e-styleurl/src/dev/jta/demo9/Cartao.java"
GERADO=$(grep -o '"scopedCss": "[^"]*"' "$BUILD/e2e-styleurl/classes/META-INF/jta/components.json")
echo "CSS escopado gerado: $GERADO"
if ! grep -q 'color: green' "$BUILD/e2e-styleurl/classes/META-INF/jta/components.json" || \
   ! grep -q 'data-jta-component' "$BUILD/e2e-styleurl/classes/META-INF/jta/components.json"; then
    echo "ERRO: styleUrl nao foi lido/escopado corretamente!"
    cat "$BUILD/e2e-styleurl/classes/META-INF/jta/components.json"
    exit 1
fi
echo "OK, styleUrl() lido e escopado corretamente:"
grep -o '"scopedCss":[^}]*' "$BUILD/e2e-styleurl/classes/META-INF/jta/components.json"

echo
echo "==> Testando tratamento defensivo de erro upstream (simula o crash real: simbolo nao resolvido dentro da expressao de template())"
mkdir -p "$BUILD/e2e-crash/src/dev/jta/demo10" "$BUILD/e2e-crash/classes" "$BUILD/e2e-crash/sources"
cat > "$BUILD/e2e-crash/src/dev/jta/demo10/ComponenteQuebrado.java" << 'EOF'
package dev.jta.demo10;
import dev.jta.core.AComponent;
@AComponent(template = "<div>" + SimboloQueNaoExiste.NAV + "<h1>oi</h1></div>")
public class ComponenteQuebrado {}
EOF
if javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-crash/classes" -s "$BUILD/e2e-crash/sources" \
      "$BUILD/e2e-crash/src/dev/jta/demo10/ComponenteQuebrado.java" 2>"$BUILD/crash-output.txt"; then
    echo "ERRO: o build deveria ter falhado - SimboloQueNaoExiste nao existe!"
    exit 1
fi
if grep -q "uncaught exception\|AnnotationTypeMismatchException" "$BUILD/crash-output.txt"; then
    echo "ERRO: o processor travou com excecao nao tratada em vez de reportar um erro limpo!"
    cat "$BUILD/crash-output.txt"
    exit 1
fi
echo "OK, build falhou com erro limpo do javac (nao um crash do processor):"
cat "$BUILD/crash-output.txt"

echo
echo "==> Testando @Layout + <router-outlet/> (caminho feliz: layout + pagina usando ele)"
mkdir -p "$BUILD/e2e-layout/src/dev/jta/demo11" "$BUILD/e2e-layout/classes" "$BUILD/e2e-layout/sources"
cat > "$BUILD/e2e-layout/src/dev/jta/demo11/SiteLayout.java" << 'EOF'
package dev.jta.demo11;
import dev.jta.core.Layout;
@Layout(template = "<nav>{{ titulo }}</nav><router-outlet/>")
public class SiteLayout {
    public String titulo = "Meu Site";
}
EOF
cat > "$BUILD/e2e-layout/src/dev/jta/demo11/PaginaComLayout.java" << 'EOF'
package dev.jta.demo11;
import dev.jta.core.AComponent;
import dev.jta.core.Route;
@Route(value = "/ola", layout = SiteLayout.class)
@AComponent(template = "<h1>Ola</h1>")
public class PaginaComLayout {}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-layout/classes" -s "$BUILD/e2e-layout/sources" \
      "$BUILD/e2e-layout/src/dev/jta/demo11/SiteLayout.java" "$BUILD/e2e-layout/src/dev/jta/demo11/PaginaComLayout.java"
echo "--- SiteLayout.jte gerado (esperado: dois @param, router-outlet -> \$unsafe{content}) ---"
cat "$BUILD/e2e-layout/sources/jta-templates/dev/jta/demo11/SiteLayout.jte"
echo
if ! grep -q '@param String content' "$BUILD/e2e-layout/sources/jta-templates/dev/jta/demo11/SiteLayout.jte" || \
   ! grep -q '\$unsafe{content}' "$BUILD/e2e-layout/sources/jta-templates/dev/jta/demo11/SiteLayout.jte"; then
    echo "ERRO: router-outlet nao foi substituido corretamente!"
    exit 1
fi
if ! grep -q '"isLayout": true' "$BUILD/e2e-layout/classes/META-INF/jta/components.json" || \
   ! grep -q '"layoutFqn": "dev.jta.demo11.SiteLayout"' "$BUILD/e2e-layout/classes/META-INF/jta/components.json"; then
    echo "ERRO: components.json nao registrou isLayout/layoutFqn corretamente!"
    cat "$BUILD/e2e-layout/classes/META-INF/jta/components.json"
    exit 1
fi
echo "OK, @Layout + router-outlet + @Route(layout=...) funcionam de ponta a ponta."

echo
echo "==> Testando erro: @Layout sem nenhum <router-outlet/>"
mkdir -p "$BUILD/e2e-layout-erro/src/dev/jta/demo12"
cat > "$BUILD/e2e-layout-erro/src/dev/jta/demo12/LayoutSemOutlet.java" << 'EOF'
package dev.jta.demo12;
import dev.jta.core.Layout;
@Layout(template = "<nav>sem outlet aqui</nav>")
public class LayoutSemOutlet {}
EOF
mkdir -p "$BUILD/e2e-layout-erro/classes" "$BUILD/e2e-layout-erro/sources"
if javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-layout-erro/classes" -s "$BUILD/e2e-layout-erro/sources" \
      "$BUILD/e2e-layout-erro/src/dev/jta/demo12/LayoutSemOutlet.java" 2>"$BUILD/layout-erro-output.txt"; then
    echo "ERRO: deveria ter falhado - layout sem router-outlet!"
    exit 1
fi
echo "OK, build falhou como esperado:"
cat "$BUILD/layout-erro-output.txt"

echo
echo "==> Testando erro: @Route(layout=...) apontando para classe sem @Layout"
mkdir -p "$BUILD/e2e-layout-erro2/src/dev/jta/demo13"
cat > "$BUILD/e2e-layout-erro2/src/dev/jta/demo13/NaoEhLayout.java" << 'EOF'
package dev.jta.demo13;
public class NaoEhLayout {}
EOF
cat > "$BUILD/e2e-layout-erro2/src/dev/jta/demo13/PaginaComLayoutErrado.java" << 'EOF'
package dev.jta.demo13;
import dev.jta.core.AComponent;
import dev.jta.core.Route;
@Route(value = "/errado", layout = NaoEhLayout.class)
@AComponent(template = "<h1>oi</h1>")
public class PaginaComLayoutErrado {}
EOF
mkdir -p "$BUILD/e2e-layout-erro2/classes" "$BUILD/e2e-layout-erro2/sources"
if javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-layout-erro2/classes" -s "$BUILD/e2e-layout-erro2/sources" \
      "$BUILD/e2e-layout-erro2/src/dev/jta/demo13/NaoEhLayout.java" "$BUILD/e2e-layout-erro2/src/dev/jta/demo13/PaginaComLayoutErrado.java" \
      2>"$BUILD/layout-erro2-output.txt"; then
    echo "ERRO: deveria ter falhado - layout() aponta pra classe sem @Layout!"
    exit 1
fi
echo "OK, build falhou como esperado:"
cat "$BUILD/layout-erro2-output.txt"

echo
echo "==> Testando erro: @Layout com mais de um <router-outlet/>"
mkdir -p "$BUILD/e2e-layout-erro3/src/dev/jta/demo14"
cat > "$BUILD/e2e-layout-erro3/src/dev/jta/demo14/LayoutComDoisOutlets.java" << 'EOF'
package dev.jta.demo14;
import dev.jta.core.Layout;
@Layout(template = "<div><router-outlet/><router-outlet/></div>")
public class LayoutComDoisOutlets {}
EOF
mkdir -p "$BUILD/e2e-layout-erro3/classes" "$BUILD/e2e-layout-erro3/sources"
if javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-layout-erro3/classes" -s "$BUILD/e2e-layout-erro3/sources" \
      "$BUILD/e2e-layout-erro3/src/dev/jta/demo14/LayoutComDoisOutlets.java" 2>"$BUILD/layout-erro3-output.txt"; then
    echo "ERRO: deveria ter falhado - dois router-outlet!"
    exit 1
fi
echo "OK, build falhou como esperado:"
cat "$BUILD/layout-erro3-output.txt"

echo
echo "==> Testando PageShellRenderer + feature flag do TailwindCSS (jta-runtime, so depende de jta-core)"
mkdir -p "$BUILD/pageshell/classes"
javac -cp "$BUILD/core-classes" -d "$BUILD/pageshell/classes" \
      "$ROOT/jta-runtime/src/main/java/dev/jta/runtime/PageShellRenderer.java" \
      "$ROOT/scripts/dev/jta/runtime/PageShellTest.java"
java -cp "$BUILD/pageshell/classes:$BUILD/core-classes" dev.jta.runtime.PageShellTest \
      "$ROOT/scripts/pageshell-fixtures/cp-empty" "$ROOT/scripts/pageshell-fixtures/cp-styled"

echo
echo "==> Testando null-safety ({{ campo? }} / {{ campo! }} em campos @Nullable)"
mkdir -p "$BUILD/e2e-nullsafety/src/dev/jta/demo15" "$BUILD/e2e-nullsafety/classes" "$BUILD/e2e-nullsafety/sources"
cat > "$BUILD/e2e-nullsafety/src/dev/jta/demo15/Nullable.java" << 'EOF'
package dev.jta.demo15;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
// anotacao minima so pra testar deteccao por NOME (nao pacote) - o processor
// reconhece qualquer @Nullable, de qualquer lib (JSpecify, javax, etc.)
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface Nullable {}
EOF
cat > "$BUILD/e2e-nullsafety/src/dev/jta/demo15/PerfilOk.java" << 'EOF'
package dev.jta.demo15;
import dev.jta.core.AComponent;
@AComponent(template = "<div><p>{{ apelido? }}</p><p>{{ nomeCompleto! }}</p><p>{{ email }}</p></div>")
public class PerfilOk {
    @Nullable public String apelido;
    @Nullable public String nomeCompleto;
    public String email = "x@y.com";
}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-nullsafety/classes" -s "$BUILD/e2e-nullsafety/sources" \
      "$BUILD/e2e-nullsafety/src/dev/jta/demo15/Nullable.java" "$BUILD/e2e-nullsafety/src/dev/jta/demo15/PerfilOk.java"
echo "--- .jte gerado (esperado: ternario para ?, Objects.requireNonNull para !) ---"
cat "$BUILD/e2e-nullsafety/sources/jta-templates/dev/jta/demo15/PerfilOk.jte"
echo
if ! grep -q 'self.apelido == null ? "" : self.apelido' "$BUILD/e2e-nullsafety/sources/jta-templates/dev/jta/demo15/PerfilOk.jte" || \
   ! grep -q 'Objects.requireNonNull(self.nomeCompleto' "$BUILD/e2e-nullsafety/sources/jta-templates/dev/jta/demo15/PerfilOk.jte"; then
    echo "ERRO: null-safety nao gerou o codigo esperado!"
    exit 1
fi
echo "OK, {{ campo? }} e {{ campo! }} geram o codigo esperado."

echo
echo "==> Testando erro: {{ campo }} sem sufixo num campo @Nullable"
cat > "$BUILD/e2e-nullsafety/src/dev/jta/demo15/PerfilQuebrado.java" << 'EOF'
package dev.jta.demo15;
import dev.jta.core.AComponent;
@AComponent(template = "<div><p>{{ apelido }}</p></div>")
public class PerfilQuebrado {
    @Nullable public String apelido;
}
EOF
if javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-nullsafety/classes" -s "$BUILD/e2e-nullsafety/sources" \
      "$BUILD/e2e-nullsafety/src/dev/jta/demo15/Nullable.java" \
      "$BUILD/e2e-nullsafety/src/dev/jta/demo15/PerfilQuebrado.java" 2>"$BUILD/nullsafety-erro.txt"; then
    echo "ERRO: deveria ter falhado - campo @Nullable referenciado sem sufixo!"
    exit 1
fi
echo "OK, build falhou como esperado:"
cat "$BUILD/nullsafety-erro.txt"

echo
echo "==> Testando i18n com verificacao estatica ({{ 'chave' | translate }})"
mkdir -p "$BUILD/e2e-i18n/src/dev/jta/demo16" "$BUILD/e2e-i18n/classes" "$BUILD/e2e-i18n/sources"
cat > "$BUILD/e2e-i18n/classes/messages.properties" << 'EOF'
saudacao.titulo=Ola
saudacao.subtitulo=Bem-vindo
EOF
cat > "$BUILD/e2e-i18n/src/dev/jta/demo16/SaudacaoI18n.java" << 'EOF'
package dev.jta.demo16;
import dev.jta.core.AComponent;
@AComponent(template = "<div><h1>{{ 'saudacao.titulo' | translate }}</h1><p>{{ 'saudacao.subtitulo' | translate }}</p></div>")
public class SaudacaoI18n {}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-i18n/classes" -s "$BUILD/e2e-i18n/sources" \
      "$BUILD/e2e-i18n/src/dev/jta/demo16/SaudacaoI18n.java"
echo "--- .jte gerado (esperado: Translations.translate) ---"
cat "$BUILD/e2e-i18n/sources/jta-templates/dev/jta/demo16/SaudacaoI18n.jte"
echo
if ! grep -q 'Translations.translate("saudacao.titulo")' "$BUILD/e2e-i18n/sources/jta-templates/dev/jta/demo16/SaudacaoI18n.jte"; then
    echo "ERRO: i18n nao gerou o codigo esperado!"
    exit 1
fi
echo "OK, {{ 'chave' | translate }} valida contra messages.properties e gera o codigo esperado."

echo
echo "==> Testando erro: chave de traducao que nao existe em messages.properties"
cat > "$BUILD/e2e-i18n/src/dev/jta/demo16/SaudacaoQuebrada.java" << 'EOF'
package dev.jta.demo16;
import dev.jta.core.AComponent;
@AComponent(template = "<div>{{ 'saudacao.titulooo' | translate }}</div>")
public class SaudacaoQuebrada {}
EOF
if javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-i18n/classes" -s "$BUILD/e2e-i18n/sources" \
      "$BUILD/e2e-i18n/src/dev/jta/demo16/SaudacaoQuebrada.java" 2>"$BUILD/i18n-erro.txt"; then
    echo "ERRO: deveria ter falhado - chave inexistente!"
    exit 1
fi
echo "OK, build falhou como esperado:"
cat "$BUILD/i18n-erro.txt"

# valida tambem a Translations em runtime (Java puro, ResourceBundle do JDK)
mkdir -p "$BUILD/i18n-runtime-test"
cat > "$BUILD/i18n-runtime-test/RuntimeI18nTest.java" << 'EOF'
import dev.jta.core.Translations;
public class RuntimeI18nTest {
    public static void main(String[] args) {
        String titulo = Translations.translate("saudacao.titulo");
        String faltando = Translations.translate("chave.que.nao.existe");
        System.out.println("titulo=" + titulo);
        System.out.println("faltando=" + faltando);
        if (!titulo.equals("Ola")) { System.out.println("FALHOU: titulo errado"); System.exit(1); }
        if (!faltando.equals("???chave.que.nao.existe???")) { System.out.println("FALHOU: fallback errado"); System.exit(1); }
        System.out.println("Translations runtime OK");
    }
}
EOF
javac -cp "$BUILD/core-classes" -d "$BUILD/i18n-runtime-test" "$BUILD/i18n-runtime-test/RuntimeI18nTest.java"
java -cp "$BUILD/i18n-runtime-test:$BUILD/core-classes:$BUILD/e2e-i18n/classes" RuntimeI18nTest

echo
echo "==> Testando jta-cli (jta init / jta new component)"
mkdir -p "$BUILD/cli-classes"
javac -d "$BUILD/cli-classes" "$ROOT/jta-cli/src/main/java/dev/jta/cli/"*.java
mkdir -p "$BUILD/cli-workspace"
cd "$BUILD/cli-workspace"
rm -rf meu-projeto
java -cp "$BUILD/cli-classes" dev.jta.cli.Main init meu-projeto
python3 -c "import xml.dom.minidom; xml.dom.minidom.parse('meu-projeto/pom.xml')" || { echo "ERRO: pom.xml gerado nao e XML valido"; exit 1; }
echo "OK: jta init gerou um projeto com pom.xml valido"

# valida que o componente de exemplo gerado compila de verdade contra o processor real
mkdir -p "$BUILD/cli-verify/src/com/example/app" "$BUILD/cli-verify/classes" "$BUILD/cli-verify/sources"
cp meu-projeto/src/main/java/com/example/app/Ola.java "$BUILD/cli-verify/src/com/example/app/"
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/cli-verify/classes" -s "$BUILD/cli-verify/sources" \
      "$BUILD/cli-verify/src/com/example/app/Ola.java"
echo "OK: componente de exemplo gerado por 'jta init' compila de verdade contra o processor real"

# jta new component com inferencia de pacote
mkdir -p meu-projeto/src/main/java/com/example/app/produtos
cd meu-projeto/src/main/java/com/example/app/produtos
java -cp "$BUILD/cli-classes" dev.jta.cli.Main new component Botao
if ! grep -q "^package com.example.app.produtos;" Botao.java; then
    echo "ERRO: jta new component nao inferiu o pacote corretamente!"
    cat Botao.java
    exit 1
fi
echo "OK: jta new component inferiu o pacote 'com.example.app.produtos' corretamente a partir do caminho"
cd "$ROOT"

echo
echo "==> Testando @RequiresRole/@AllowAnonymous (validacao contra enum configurado em jta.config.toml)"
mkdir -p "$BUILD/e2e-security/src/dev/jta/demo17" "$BUILD/e2e-security/classes" "$BUILD/e2e-security/sources"
cat > "$BUILD/e2e-security/classes/jta.config.toml" << 'EOF'
[security]
roles_enum = "dev.jta.demo17.AppRoles"
EOF
cat > "$BUILD/e2e-security/src/dev/jta/demo17/AppRoles.java" << 'EOF'
package dev.jta.demo17;
public enum AppRoles { ADMIN, EDITOR, VIEWER }
EOF
cat > "$BUILD/e2e-security/src/dev/jta/demo17/AdminPage.java" << 'EOF'
package dev.jta.demo17;
import dev.jta.core.AComponent;
import dev.jta.core.RequiresRole;
@RequiresRole("ADMIN")
@AComponent(template = "<div>admin</div>")
public class AdminPage {}
EOF
cat > "$BUILD/e2e-security/src/dev/jta/demo17/PublicPage.java" << 'EOF'
package dev.jta.demo17;
import dev.jta.core.AComponent;
import dev.jta.core.AllowAnonymous;
@AllowAnonymous
@AComponent(template = "<div>publico</div>")
public class PublicPage {}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-security/classes" -s "$BUILD/e2e-security/sources" \
      "$BUILD/e2e-security/src/dev/jta/demo17/AppRoles.java" \
      "$BUILD/e2e-security/src/dev/jta/demo17/AdminPage.java" \
      "$BUILD/e2e-security/src/dev/jta/demo17/PublicPage.java"
if ! grep -q '"requiredRoles": \["ADMIN"\]' "$BUILD/e2e-security/classes/META-INF/jta/components.json"; then
    echo "ERRO: requiredRoles nao foi registrado corretamente!"
    cat "$BUILD/e2e-security/classes/META-INF/jta/components.json"
    exit 1
fi
if ! grep -q '"allowAnonymous": true' "$BUILD/e2e-security/classes/META-INF/jta/components.json"; then
    echo "ERRO: allowAnonymous nao foi registrado corretamente!"
    exit 1
fi
echo "OK: @RequiresRole validado contra o enum configurado, @AllowAnonymous registrado corretamente."

echo
echo "==> Testando erro: role que nao existe no enum configurado"
cat > "$BUILD/e2e-security/src/dev/jta/demo17/PaginaComRoleErrada.java" << 'EOF'
package dev.jta.demo17;
import dev.jta.core.AComponent;
import dev.jta.core.RequiresRole;
@RequiresRole("ADMINN")
@AComponent(template = "<div>oi</div>")
public class PaginaComRoleErrada {}
EOF
if javac -cp "$BUILD/core-classes:$BUILD/e2e-security/classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-security/classes" -s "$BUILD/e2e-security/sources" \
      "$BUILD/e2e-security/src/dev/jta/demo17/PaginaComRoleErrada.java" 2>"$BUILD/security-erro1.txt"; then
    echo "ERRO: deveria ter falhado - role 'ADMINN' nao existe no enum!"
    exit 1
fi
echo "OK, build falhou como esperado:"
cat "$BUILD/security-erro1.txt"

echo
echo "==> Testando erro: @RequiresRole e @AllowAnonymous juntos (contraditorio)"
cat > "$BUILD/e2e-security/src/dev/jta/demo17/PaginaContraditoria.java" << 'EOF'
package dev.jta.demo17;
import dev.jta.core.AComponent;
import dev.jta.core.AllowAnonymous;
import dev.jta.core.RequiresRole;
@RequiresRole("ADMIN")
@AllowAnonymous
@AComponent(template = "<div>oi</div>")
public class PaginaContraditoria {}
EOF
if javac -cp "$BUILD/core-classes:$BUILD/e2e-security/classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-security/classes" -s "$BUILD/e2e-security/sources" \
      "$BUILD/e2e-security/src/dev/jta/demo17/PaginaContraditoria.java" 2>"$BUILD/security-erro2.txt"; then
    echo "ERRO: deveria ter falhado - @RequiresRole + @AllowAnonymous e contraditorio!"
    exit 1
fi
echo "OK, build falhou como esperado:"
cat "$BUILD/security-erro2.txt"

echo
echo "==> Testando @Sse (registro de ssePath/sseIntervalMillis)"
mkdir -p "$BUILD/e2e-sse/src/dev/jta/demo18" "$BUILD/e2e-sse/classes" "$BUILD/e2e-sse/sources"
cat > "$BUILD/e2e-sse/src/dev/jta/demo18/Notificacoes.java" << 'EOF'
package dev.jta.demo18;
import dev.jta.core.AComponent;
import dev.jta.core.Sse;
@Sse(value = "/notificacoes", intervalMillis = 2000)
@AComponent(template = "<div>{{ contador }}</div>")
public class Notificacoes {
    public int contador = 0;
}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-sse/classes" -s "$BUILD/e2e-sse/sources" \
      "$BUILD/e2e-sse/src/dev/jta/demo18/Notificacoes.java"
if ! grep -q '"ssePath": "/notificacoes"' "$BUILD/e2e-sse/classes/META-INF/jta/components.json" || \
   ! grep -q '"sseIntervalMillis": 2000' "$BUILD/e2e-sse/classes/META-INF/jta/components.json"; then
    echo "ERRO: @Sse nao foi registrado corretamente!"
    cat "$BUILD/e2e-sse/classes/META-INF/jta/components.json"
    exit 1
fi
echo "OK: @Sse registrado corretamente em components.json."

echo
echo "==> Testando bindableFields (seguro por padrao: so campo referenciado no template, ou path param, ou @Bindable)"
mkdir -p "$BUILD/e2e-bindable/src/dev/jta/demo19" "$BUILD/e2e-bindable/classes" "$BUILD/e2e-bindable/sources"
cat > "$BUILD/e2e-bindable/src/dev/jta/demo19/Perfil.java" << 'EOF'
package dev.jta.demo19;
import dev.jta.core.AComponent;
import dev.jta.core.Bindable;
import dev.jta.core.Route;
@Route("/perfil/{id}")
@AComponent(template = "<div><input type=\"hidden\" name=\"nome\" value=\"{{ nome }}\"/><p>{{ nome }}</p></div>")
public class Perfil {
    public String id;              // path param - deve entrar em bindableFields mesmo sem {{ id }}
    public String nome = "";       // referenciado em {{ nome }} - deve entrar
    public boolean isAdmin = false;   // NUNCA referenciado no template - NAO deve entrar (o achado #5 do SECURITY.md)
    @Bindable
    public int pagina = 0;         // nunca referenciado, mas com @Bindable - deve entrar
}
EOF
javac -cp "$BUILD/core-classes" \
      -processorpath "$BUILD/processor-classes:$BUILD/core-classes" \
      -d "$BUILD/e2e-bindable/classes" -s "$BUILD/e2e-bindable/sources" \
      "$BUILD/e2e-bindable/src/dev/jta/demo19/Perfil.java"
JSON=$(cat "$BUILD/e2e-bindable/classes/META-INF/jta/components.json")
echo "$JSON" | grep '"bindableFields"'
if ! echo "$JSON" | grep -q '"bindableFields": \["nome", "pagina", "id"\]'; then
    echo "ERRO: bindableFields nao ficou como esperado (esperava exatamente nome, id, pagina - sem isAdmin)!"
    exit 1
fi
echo "OK: 'nome' (interpolado) e 'id' (path param) e 'pagina' (@Bindable) entraram; 'isAdmin' (nunca referenciado) NAO entrou."

echo "==> Tudo validado com sucesso."
