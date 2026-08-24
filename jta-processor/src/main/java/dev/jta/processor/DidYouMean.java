package dev.jta.processor;

import java.util.Set;

/**
 * Sugestao "voce quis dizer X?" via distancia de edicao, compartilhada
 * entre {@link TemplateTransformer} (bindings de template) e
 * {@link JtaAnnotationProcessor} (parametros de rota) - parte do
 * compromisso de DX do documento original: erros de compilacao devem ser
 * acionaveis, nao so apontar o problema.
 */
final class DidYouMean {

    private DidYouMean() {
    }

    static String suggest(String reference, Set<String> candidates) {
        String best = null;
        int bestDistance = Integer.MAX_VALUE;
        for (String candidate : candidates) {
            int distance = levenshtein(reference, candidate);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        if (best != null && bestDistance <= Math.max(2, reference.length() / 2)) {
            return " (voce quis dizer '" + best + "'?)";
        }
        return "";
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[a.length()][b.length()];
    }
}
