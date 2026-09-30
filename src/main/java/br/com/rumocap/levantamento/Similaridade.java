package br.com.rumocap.levantamento;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.rumocap.util.TextoUtils;

/**
 * Medidas de semelhança entre nomes e endereços (de 0 a 1), usadas para
 * perceber que "Restaurante do João" e "Restaurante João" são o mesmo local.
 */
public final class Similaridade {

    /**
     * Palavras que não ajudam a distinguir um estabelecimento de outro. O nome do
     * município entra aqui porque aparece em muitos nomes oficiais ("CEO de Capitão Poço").
     */
    private static final Set<String> PALAVRAS_VAZIAS = Set.of(
            "de", "da", "do", "das", "dos", "e", "o", "a", "os", "as", "em", "no", "na", "nos", "nas",
            "ltda", "me", "eireli", "epp", "sa", "mei", "cia", "capitao", "poco", "municipal", "municipio",
            "pa", "para");

    private static final Map<String, String> ABREVIACOES_ENDERECO = Map.ofEntries(
            Map.entry("av", "avenida"), Map.entry("trav", "travessa"), Map.entry("tv", "travessa"),
            Map.entry("tr", "travessa"), Map.entry("r", "rua"), Map.entry("rod", "rodovia"),
            Map.entry("est", "estrada"), Map.entry("pca", "praca"), Map.entry("dr", "doutor"),
            Map.entry("gov", "governador"), Map.entry("pres", "presidente"), Map.entry("sen", "senador"),
            Map.entry("cel", "coronel"), Map.entry("sta", "santa"), Map.entry("sto", "santo"));

    private static final Pattern NAO_ALFANUMERICO = Pattern.compile("[^a-z0-9]+");
    private static final Pattern NUMERO_DO_ENDERECO = Pattern.compile(",\\s*(\\d+)");

    private Similaridade() {
    }

    /** Palavras significativas do nome, sem acentos e sem conectivos. */
    public static List<String> palavras(String texto) {
        List<String> palavras = new ArrayList<>();
        for (String parte : NAO_ALFANUMERICO.split(TextoUtils.normalizar(texto))) {
            if (!parte.isEmpty() && !PALAVRAS_VAZIAS.contains(parte)) {
                palavras.add(parte);
            }
        }
        return palavras;
    }

    /**
     * Semelhança entre dois nomes, de 0 (diferentes) a 1 (iguais).
     * <p>
     * Compara a parte distintiva dos nomes: "Lanchonete Diza" e "Lanchonete Kedy"
     * só têm em comum o tipo de negócio, então não são parecidas. Quando os nomes
     * indicam tipos de negócio diferentes ("Mercadinho Bom Jesus" e "Panificadora
     * Bom Jesus"), a semelhança é reduzida.
     */
    public static double nomes(String nomeA, String nomeB) {
        List<String> a = palavras(nomeA);
        List<String> b = palavras(nomeB);
        if (a.isEmpty() || b.isEmpty()) {
            return 0;
        }
        List<String> distintivasA = a.stream().filter(palavra -> !Vocabulario.GENERICAS_MINUSCULAS.contains(palavra)).toList();
        List<String> distintivasB = b.stream().filter(palavra -> !Vocabulario.GENERICAS_MINUSCULAS.contains(palavra)).toList();
        double semelhanca = distintivasA.isEmpty() || distintivasB.isEmpty()
                ? medida(a, b)
                : medida(distintivasA, distintivasB);

        String tipoA = Categorias.pelaDescricao(nomeA);
        String tipoB = Categorias.pelaDescricao(nomeB);
        if (tipoA != null && tipoB != null && !tipoA.equals(tipoB)) {
            semelhanca *= 0.7;
        }
        return semelhanca;
    }

    private static double medida(List<String> a, List<String> b) {
        Set<String> conjuntoA = new HashSet<>(a);
        Set<String> conjuntoB = new HashSet<>(b);
        Set<String> comuns = new HashSet<>(conjuntoA);
        comuns.retainAll(conjuntoB);
        Set<String> todas = new HashSet<>(conjuntoA);
        todas.addAll(conjuntoB);

        double jaccard = (double) comuns.size() / todas.size();
        int menor = Math.min(conjuntoA.size(), conjuntoB.size());
        // "Pro Dent" dentro de "Dental Pro Dent": só vale para nomes com duas palavras ou mais
        double contencao = menor >= 2 ? (double) comuns.size() / menor : jaccard;
        double letras = dice(String.join("", a), String.join("", b));
        return Math.max(Math.max(jaccard, contencao), letras);
    }

    /** Coeficiente de Dice sobre pares de letras (tolera pequenos erros de digitação). */
    static double dice(String a, String b) {
        if (a.equals(b)) {
            return a.isEmpty() ? 0 : 1;
        }
        if (a.length() < 2 || b.length() < 2) {
            return 0;
        }
        Map<String, Integer> paresA = new HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            paresA.merge(a.substring(i, i + 2), 1, Integer::sum);
        }
        int comuns = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String par = b.substring(i, i + 2);
            Integer restantes = paresA.get(par);
            if (restantes != null && restantes > 0) {
                comuns++;
                paresA.put(par, restantes - 1);
            }
        }
        return 2.0 * comuns / (a.length() - 1 + b.length() - 1);
    }

    /**
     * Semelhança entre endereços. Quando os dois têm número, exige o mesmo número
     * e compara o nome da rua; sem número, compara o texto inteiro.
     */
    public static double enderecos(String enderecoA, String enderecoB) {
        if (enderecoA == null || enderecoB == null) {
            return 0;
        }
        String numeroA = numero(enderecoA);
        String numeroB = numero(enderecoB);
        String ruaA = rua(enderecoA);
        String ruaB = rua(enderecoB);
        if (ruaA.isEmpty() || ruaB.isEmpty()) {
            return 0;
        }
        if (numeroA != null && numeroB != null) {
            return numeroA.equals(numeroB) ? dice(ruaA, ruaB) : 0;
        }
        return dice(ruaA, ruaB) * 0.8; // sem número, a certeza é menor
    }

    private static String numero(String endereco) {
        Matcher numero = NUMERO_DO_ENDERECO.matcher(endereco);
        return numero.find() ? numero.group(1).replaceFirst("^0+(?=\\d)", "") : null;
    }

    /** Nome da rua normalizado e sem abreviações, sem número e sem bairro. */
    private static String rua(String endereco) {
        String semBairro = endereco.split("\\s+-\\s+|,", 2)[0];
        StringBuilder rua = new StringBuilder();
        for (String parte : NAO_ALFANUMERICO.split(TextoUtils.normalizar(semBairro))) {
            if (!parte.isEmpty() && !PALAVRAS_VAZIAS.contains(parte)) {
                rua.append(ABREVIACOES_ENDERECO.getOrDefault(parte, parte));
            }
        }
        return rua.toString();
    }
}
