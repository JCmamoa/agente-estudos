package agenteestudos.ml;

import ai.djl.Model;
import ai.djl.ndarray.NDManager;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Lote offline do módulo ML: lê planos/plano-de-estudos.csv (padrão Google
 * Agenda), prevê carga sustentável (TensorFlow) e risco de atraso (PyTorch)
 * por dia e grava planos/insights-ml.json. O agente ADK lê esse JSON com
 * ReadFile no replanejamento — o fluxo do agente não muda.
 */
public final class Main {

    private static final DateTimeFormatter MDY = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter YMD = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter H24 = DateTimeFormatter.ofPattern("H:mm[:ss]");
    private static final DateTimeFormatter H12 =
            DateTimeFormatter.ofPattern("h:mm[:ss] a", Locale.US);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    record Sessao(String assunto, LocalDate data, String inicio, String fim,
                  int minutos, String descricao) {}

    record SessaoJson(String assunto, String inicio, String fim, String descricao) {}

    record Dia(String data, int minutosPlanejados, int minutosSustentaveisPrevistos,
               double probAtraso, String nivel, int nSessoes, List<SessaoJson> sessoes) {}

    record Resumo(int diasAnalisados, int diasRisco, int minutosExcedentes) {}

    public static void main(String[] args) throws IOException {
        Path csv = Path.of(args.length > 0 ? args[0] : "../planos/plano-de-estudos.csv");
        Path destino = csv.getParent() == null ? Path.of(".") : csv.getParent();

        if (Files.notExists(csv)) {
            Map<String, Object> vazio = new LinkedHashMap<>();
            vazio.put("estado", "sem_plano");
            vazio.put("mensagem",
                    "Nenhum plano em " + csv + "; gere um plano pelo agente e rode o módulo ML.");
            Files.writeString(destino.resolve("insights-ml.json"), GSON.toJson(vazio),
                    StandardCharsets.UTF_8);
            System.out.println("sem plano — gravado: " + destino.resolve("insights-ml.json"));
            return;
        }

        LocalDate hoje = LocalDate.now();
        Map<LocalDate, List<Sessao>> porDia = agrupar(csv, hoje);

        // Um manager por engine: nunca misturar tensores entre engines.
        try (NDManager managerTF = NDManager.newBaseManager("TensorFlow");
             NDManager managerPT = NDManager.newBaseManager("PyTorch")) {
            float[] pesosCarga = Modelos.treinarCarga(managerTF);
            Model risco = Modelos.treinarRisco(managerPT);

            List<Dia> dias = new ArrayList<>();
            int excedente = 0;
            int riscoAlto = 0;
            for (var e : porDia.entrySet()) { // TreeMap já vem ordenado
                LocalDate data = e.getKey();
                List<Sessao> sessoes = e.getValue();
                int minutosPlan = sessoes.stream().mapToInt(Sessao::minutos).sum();
                float minutosPlanNorm = minutosPlan / Modelos.ESCALA_MINUTOS;
                float diaSemanaNorm = (data.getDayOfWeek().getValue() % 7) / 6f;
                int nSessoes = sessoes.size();

                float capacidadeOut = Modelos.previrCarga(managerTF, pesosCarga,
                        minutosPlanNorm, nSessoes / 6f, diaSemanaNorm);
                int minutosSust = Math.round(capacidadeOut);

                float probOut = Modelos.preverRisco(risco, managerPT, new float[] {
                        minutosPlanNorm, minutosSust / Modelos.ESCALA_MINUTOS, diaSemanaNorm});
                double prob = Math.max(0, Math.min(1.0, probOut));
                String nivel = prob < 0.2 ? "baixo" : (prob < 0.5 ? "medio" : "alto");

                if (minutosPlan > minutosSust) excedente += minutosPlan - minutosSust;
                if (prob >= 0.5) riscoAlto++;

                List<SessaoJson> sj = sessoes.stream()
                        .map(s -> new SessaoJson(s.assunto(), s.inicio(), s.fim(), s.descricao()))
                        .toList();
                dias.add(new Dia(data.toString(), minutosPlan, minutosSust, prob, nivel,
                        nSessoes, sj));
            }

            Map<String, Object> saida = new LinkedHashMap<>();
            saida.put("estado", "ok");
            saida.put("gerado_em", hoje.toString());
            saida.put("engine_carga", "TensorFlow");
            saida.put("engine_risco", "PyTorch");
            saida.put("dias", dias);
            saida.put("resumo", new Resumo(dias.size(), riscoAlto, excedente));

            Path json = destino.resolve("insights-ml.json");
            Files.writeString(json, GSON.toJson(saida), StandardCharsets.UTF_8);
            System.out.printf(Locale.ROOT,
                    "insights-ml.json gravado: %d dia(s), %d em risco, %d min excedentes%n",
                    dias.size(), riscoAlto, excedente);
        }
    }

    private static Map<LocalDate, List<Sessao>> agrupar(Path csv, LocalDate hoje) {
        TreeMap<LocalDate, List<Sessao>> mapa = new TreeMap<>();
        for (CsvUtil.Linha l : CsvUtil.ler(csv)) {
            List<String> c = l.campos();
            if (c.size() < 5) continue;
            String assunto = c.get(0).strip();
            if (assunto.isEmpty() || assunto.equalsIgnoreCase("Subject")) continue;
            LocalDate data = parseData(c.get(1).strip());
            if (data == null || data.isBefore(hoje)) continue;
            String inicio = c.get(2).strip();
            String fim = c.get(4).strip();
            int minutos = duracao(inicio, fim);
            String descricao = c.size() > 6 ? c.get(6).strip() : "";
            mapa.computeIfAbsent(data, k -> new ArrayList<>())
                .add(new Sessao(assunto, data, inicio, fim, minutos, descricao));
        }
        return mapa;
    }

    static LocalDate parseData(String bruta) {
        for (DateTimeFormatter f : List.of(MDY, YMD)) {
            try {
                return LocalDate.parse(bruta, f);
            } catch (RuntimeException ignorada) {
                // tenta o próximo formato
            }
        }
        return null;
    }

    static int duracao(String inicioBruto, String fimBruto) {
        try {
            LocalTime i = parseHora(inicioBruto);
            LocalTime f = parseHora(fimBruto);
            int d = (int) Duration.between(i, f).toMinutes();
            return d <= 0 || d > 480 ? 45 : d; // fallback: sessão padrão de 45 min
        } catch (RuntimeException ignorada) {
            return 45;
        }
    }

    private static LocalTime parseHora(String bruta) {
        String s = bruta.strip();
        String maiuscula = s.toUpperCase(Locale.ROOT);
        if (maiuscula.endsWith("PM") || maiuscula.endsWith("AM")) {
            return LocalTime.parse(s, H12); // token 'a' resolve AM/PM
        }
        return LocalTime.parse(s, H24);
    }
}
