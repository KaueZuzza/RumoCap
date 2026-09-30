package br.com.rumocap.levantamento;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.rumocap.util.TextoUtils;

/**
 * Traduz os horários das fontes para o português, sem alterar o conteúdo.
 * Quando o formato não é reconhecido, o texto original é mantido.
 */
public final class Horarios {

    private static final Map<String, String> DIAS = Map.of(
            "Mo", "seg.", "Tu", "ter.", "We", "qua.", "Th", "qui.",
            "Fr", "sex.", "Sa", "sáb.", "Su", "dom.", "PH", "feriados");

    private static final String DIA = "(?:Mo|Tu|We|Th|Fr|Sa|Su|PH)";
    private static final Pattern REGRA = Pattern.compile(
            "^(" + DIA + "(?:\\s*-\\s*" + DIA + ")?(?:\\s*,\\s*" + DIA + "(?:\\s*-\\s*" + DIA + ")?)*)?\\s*(.*)$");
    private static final Pattern INTERVALO = Pattern.compile("(\\d{1,2}:\\d{2})\\s*-\\s*(\\d{1,2}:\\d{2})\\+?");

    private Horarios() {
    }

    /**
     * Converte o horário do OpenStreetMap (opening_hours), por exemplo
     * "Mo-Sa 08:00-12:00,14:00-18:00; Su off" em
     * "Seg. a sáb.: 08:00–12:00 e 14:00–18:00" / "Dom.: fechado".
     */
    public static String deOpenStreetMap(String original) {
        String texto = TextoUtils.limpar(original);
        if (texto == null) {
            return null;
        }
        if (texto.equals("24/7")) {
            return "Todos os dias, 24 horas";
        }

        // dias -> faixas de horário, mantendo a ordem e juntando regras com os mesmos dias
        Map<String, List<String>> porDias = new LinkedHashMap<>();
        for (String regra : texto.split(";")) {
            if (regra.isBlank()) {
                continue;
            }
            Matcher partes = REGRA.matcher(regra.strip());
            if (!partes.matches()) {
                return texto;
            }
            String dias = partes.group(1) == null ? "" : traduzirDias(partes.group(1));
            String horas = traduzirHoras(partes.group(2));
            if (horas == null) {
                return texto;
            }
            porDias.computeIfAbsent(dias, chave -> new ArrayList<>()).add(horas);
        }
        if (porDias.isEmpty()) {
            return texto;
        }

        List<String> linhas = new ArrayList<>();
        porDias.forEach((dias, horas) -> {
            String faixa = String.join(" e ", horas);
            linhas.add(dias.isEmpty() ? maiuscula(faixa) : maiuscula(dias) + ": " + faixa);
        });
        return String.join("\n", linhas);
    }

    private static String traduzirDias(String dias) {
        List<String> partes = new ArrayList<>();
        for (String item : dias.split(",")) {
            String[] limites = item.strip().split("\\s*-\\s*");
            if (limites.length == 2) {
                partes.add(limites[0].equals("Mo") && limites[1].equals("Su")
                        ? "todos os dias"
                        : DIAS.get(limites[0]) + " a " + DIAS.get(limites[1]));
            } else {
                partes.add(DIAS.get(limites[0]));
            }
        }
        return String.join(", ", partes);
    }

    private static String traduzirHoras(String horas) {
        String texto = horas.strip();
        if (texto.isEmpty()) {
            return null;
        }
        String chave = texto.toLowerCase(Locale.ROOT);
        if (chave.equals("off") || chave.equals("closed")) {
            return "fechado";
        }
        if (chave.equals("24:00") || chave.equals("00:00-24:00")) {
            return "24 horas";
        }
        List<String> faixas = new ArrayList<>();
        for (String faixa : texto.split(",")) {
            Matcher intervalo = INTERVALO.matcher(faixa.strip());
            if (!intervalo.matches()) {
                return null;
            }
            faixas.add(intervalo.group(1) + "–" + intervalo.group(2));
        }
        return String.join(" e ", faixas);
    }

    /**
     * Converte o turno de atendimento do CNES, por exemplo
     * "ATENDIMENTOS NOS TURNOS DA MANHA E A TARDE" em "Turnos da manhã e da tarde".
     */
    public static String deCnes(String original) {
        String texto = TextoUtils.limpar(original);
        if (texto == null) {
            return null;
        }
        String chave = TextoUtils.normalizar(texto);
        if (chave.contains("24 horas")) {
            return chave.contains("sabados") || chave.contains("feriados")
                    ? "24 horas, todos os dias (plantão inclusive aos sábados, domingos e feriados)"
                    : "24 horas";
        }
        if (chave.contains("intermitente")) {
            return "Turnos intermitentes";
        }
        boolean manha = chave.contains("manha");
        boolean tarde = chave.contains("tarde");
        boolean noite = chave.contains("noite");
        List<String> turnos = new ArrayList<>();
        if (manha) turnos.add("da manhã");
        if (tarde) turnos.add("da tarde");
        if (noite) turnos.add("da noite");
        if (turnos.isEmpty()) {
            return maiuscula(texto.toLowerCase(Locale.ROOT));
        }
        if (turnos.size() == 1) {
            return switch (turnos.get(0)) {
                case "da manhã" -> "Somente pela manhã";
                case "da tarde" -> "Somente à tarde";
                default -> "Somente à noite";
            };
        }
        String ultimo = turnos.remove(turnos.size() - 1);
        return "Turnos " + String.join(", ", turnos) + " e " + ultimo;
    }

    private static String maiuscula(String texto) {
        return texto.isEmpty() ? texto : Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
    }
}
