package br.com.rumocap.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pesquisa do guia: sem diferenciar acentos e maiúsculas e com sinônimos dos
 * tipos de estabelecimento mais procurados. Assim, "farmácia" também encontra
 * "Bibi Farma" e "Drogaria Central"; "mercado" encontra "Mercearia" etc.
 */
public final class Busca {

    private static final Map<String, List<String>> SINONIMOS = Map.ofEntries(
            Map.entry("farmacia", List.of("farma", "drogaria", "drogas", "pharma")),
            Map.entry("drogaria", List.of("farmacia", "farma")),
            Map.entry("mercado", List.of("mercadinho", "supermercado", "mercearia", "minimercado", "atacado", "atacadao")),
            Map.entry("supermercado", List.of("mercado", "mercadinho", "atacado")),
            Map.entry("mercearia", List.of("mercadinho", "mercado")),
            Map.entry("padaria", List.of("panificadora", "confeitaria")),
            Map.entry("restaurante", List.of("churrascaria", "self service", "comida", "pizzaria")),
            Map.entry("lanchonete", List.of("lanche", "hamburgueria", "burguer", "burger", "pastelaria")),
            Map.entry("lanche", List.of("lanchonete", "hamburgueria", "burguer")),
            Map.entry("oficina", List.of("mecanica", "auto pecas", "moto pecas", "borracharia")),
            Map.entry("mecanica", List.of("oficina")),
            Map.entry("posto", List.of("combustivel", "gasolina")),
            Map.entry("gasolina", List.of("posto", "combustivel")),
            Map.entry("hotel", List.of("pousada", "hospedagem", "pensao", "motel")),
            Map.entry("pousada", List.of("hotel", "hospedagem")),
            Map.entry("salao", List.of("beleza", "cabeleireir", "estetica")),
            Map.entry("cabeleireiro", List.of("salao", "barbearia", "beleza")),
            Map.entry("barbearia", List.of("barber", "barbeiro")),
            Map.entry("celular", List.of("cell", "celulares")),
            Map.entry("roupa", List.of("moda", "confecc", "boutique", "jeans")),
            Map.entry("roupas", List.of("moda", "confecc", "boutique", "jeans")),
            Map.entry("acougue", List.of("carne", "frango")),
            Map.entry("clinica", List.of("consultorio", "medic")),
            Map.entry("medico", List.of("clinica", "consultorio")),
            Map.entry("dentista", List.of("odonto", "dental")),
            Map.entry("hospital", List.of("maternidade", "pronto atendimento", "upa")),
            Map.entry("posto de saude", List.of("unidade basica", "centro de saude", "usf", "ubs", "ps ")),
            Map.entry("ubs", List.of("unidade basica", "centro de saude", "usf")),
            Map.entry("laboratorio", List.of("exames", "diagnostic", "analises")),
            Map.entry("banco", List.of("loterica", "credito", "correspondente")),
            Map.entry("construcao", List.of("ferragens", "ferragem", "constru", "material")),
            Map.entry("ferragem", List.of("ferragens", "construcao")),
            Map.entry("moveis", List.of("movelaria", "marcenaria", "colchoes")),
            Map.entry("academia", List.of("fitness", "crossfit")),
            Map.entry("lava jato", List.of("lavajato", "lava-jato")),
            Map.entry("pneu", List.of("pneus", "borracharia")),
            Map.entry("sorvete", List.of("sorveteria", "acai", "picole")),
            Map.entry("acai", List.of("acaiteria")),
            Map.entry("otica", List.of("optica", "oculos")),
            Map.entry("informatica", List.of("computador", "tech", "lan house")),
            Map.entry("internet", List.of("provedor", "lan house", "ciber")));

    private Busca() {
    }

    /** O termo normalizado e seus sinônimos (tolera plural simples: "farmácias" = "farmácia"). */
    public static Set<String> alternativas(String termo) {
        String normalizado = TextoUtils.normalizar(termo);
        Set<String> alternativas = new LinkedHashSet<>();
        if (normalizado.isEmpty()) {
            return alternativas;
        }
        alternativas.add(normalizado);
        String singular = normalizado.endsWith("s") && normalizado.length() > 4
                ? normalizado.substring(0, normalizado.length() - 1)
                : normalizado;
        alternativas.add(singular);
        alternativas.addAll(SINONIMOS.getOrDefault(normalizado, List.of()));
        alternativas.addAll(SINONIMOS.getOrDefault(singular, List.of()));
        return alternativas;
    }

    /** O texto (já normalizado) contém o termo ou algum dos seus sinônimos. */
    public static boolean corresponde(String textoNormalizado, Set<String> alternativas) {
        for (String alternativa : alternativas) {
            if (textoNormalizado.contains(alternativa)) {
                return true;
            }
        }
        return false;
    }
}
