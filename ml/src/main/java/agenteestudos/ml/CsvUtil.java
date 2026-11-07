package agenteestudos.ml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Parser minimalista de CSV no padrão Google Agenda (aspas duplas → vírgula interna). */
final class CsvUtil {
    private CsvUtil() {}

    record Linha(List<String> campos) {}

    static List<Linha> ler(Path csv) {
        try {
            List<Linha> linhas = new ArrayList<>();
            for (String bruta : Files.readAllLines(csv, StandardCharsets.UTF_8)) {
                if (bruta.isBlank()) continue;
                List<String> campos = separar(bruta);
                linhas.add(new Linha(campos));
            }
            // primeira linha é o cabeçalho
            linhas.remove(0);
            return linhas;
        } catch (IOException e) {
            throw new IllegalStateException("Falha ao ler CSV: " + csv, e);
        }
    }

    private static List<String> separar(String linha) {
        List<String> campos = new ArrayList<>();
        StringBuilder atual = new StringBuilder();
        boolean dentro = false;
        for (int i = 0; i < linha.length(); i++) {
            char c = linha.charAt(i);
            if (c == '"') {
                if (dentro && i + 1 < linha.length() && linha.charAt(i + 1) == '"') {
                    atual.append('"'); // aspas escapadas
                    i++;
                } else {
                    dentro = !dentro;
                }
            } else if (c == ',' && !dentro) {
                campos.add(atual.toString());
                atual.setLength(0);
            } else {
                atual.append(c);
            }
        }
        campos.add(atual.toString());
        return campos;
    }
}
