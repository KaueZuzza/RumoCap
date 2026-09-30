package br.com.rumocap.levantamento;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.rumocap.util.TextoUtils;

/**
 * Deixa nomes e endereços das fontes oficiais (que vêm em MAIÚSCULAS e sem
 * acento) em um formato legível: "CENTRO DE SAUDE DE CAPITAO POCO" vira
 * "Centro de Saúde de Capitão Poço".
 * <p>
 * Só muda a forma de escrever: nenhuma palavra é acrescentada ou trocada.
 * Os acentos são recolocados apenas nas palavras de um dicionário conhecido;
 * as demais ficam como vieram da fonte.
 */
public final class Capitalizacao {

    /** Palavras que ficam em minúsculas no meio do nome. */
    private static final Set<String> LIGACOES = Set.of(
            "de", "da", "do", "das", "dos", "e", "em", "na", "no", "nas", "nos", "a", "o", "as", "os",
            "ao", "aos", "com", "por", "sem");

    /** Siglas conhecidas, mantidas em maiúsculas. */
    private static final Set<String> SIGLAS = Set.of(
            "UBS", "USF", "UPA", "USB", "CAPS", "CEO", "CER", "CTA", "AME", "UOM", "CAF", "NASF", "VISA", "SUS",
            "PS", "SMS", "CNPJ", "ME", "LTDA", "EIRELI", "EPP", "SA", "PA", "BR", "CDL", "CIRETRAN", "DETRAN",
            "CECAP", "UFRA", "IBGE", "INSS", "TV", "FM", "CEP", "OAB", "CRM", "DER", "MEI", "CEF", "CNES",
            "SAMU", "UTI", "LAN", "GPS", "EJA", "APAE", "CRAS", "CREAS", "SENAC", "SENAI", "SEBRAE", "SESC");

    /** Abreviações que ganham ponto: "DR ALDOMAR" -> "Dr. Aldomar". */
    private static final Map<String, String> ABREVIACOES = Map.of(
            "DR", "Dr.", "DRA", "Dra.", "STA", "Sta.", "STO", "Sto.", "SR", "Sr.", "SRA", "Sra.", "PROF", "Prof.");

    /** Palavras de duas letras que não são iniciais (ficam como palavra comum). */
    private static final Set<String> PALAVRAS_CURTAS = Set.of(
            "de", "da", "do", "e", "em", "na", "no", "os", "as", "ao", "um", "eu", "se", "ou", "tu", "ja", "la",
            "pe", "fe", "pa", "vo", "ze", "lu", "bi", "di", "zu");

    /** Tipos de logradouro abreviados nas fontes. */
    private static final Map<String, String> LOGRADOUROS = Map.ofEntries(
            Map.entry("AV", "Avenida"), Map.entry("AVEN", "Avenida"), Map.entry("TRAV", "Travessa"),
            Map.entry("TV", "Travessa"), Map.entry("TR", "Travessa"), Map.entry("R", "Rua"),
            Map.entry("ROD", "Rodovia"), Map.entry("EST", "Estrada"), Map.entry("PCA", "Praça"),
            Map.entry("PC", "Praça"), Map.entry("AL", "Alameda"), Map.entry("VL", "Vila"),
            Map.entry("CONJ", "Conjunto"), Map.entry("PSG", "Passagem"), Map.entry("PAS", "Passagem"));

    private static final Pattern PALAVRA = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern NUMERO_ROMANO = Pattern.compile("(?i)^(I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII)$");

    private static final Map<String, String> ACENTOS = new HashMap<>();

    static {
        String[] palavras = {
            "açaí", "açougue", "acessórios", "agência", "agrícola", "agrícolas", "agropecuária", "água", "alimentação",
            "alimentícios", "aliança", "amazônia", "amazônica", "antônio", "aparência", "aquário", "aarão",
            "armazém", "assistência", "associação", "atenção", "atendimento", "auricélio", "avícola",
            "bênção", "belém", "brechó", "café", "calçados", "capitão", "cerâmica", "chácara", "cícero",
            "cláudio", "clínica", "clínicas", "colchões", "comércio", "comunicação", "conceição", "confecção",
            "confecções", "construção", "construções", "conveniência", "cosméticos", "crédito", "criação",
            "decoração", "diagnóstico", "diagnóstica", "distribuição", "doméstica", "domésticas", "educação",
            "elétrica", "elétricos", "eletrônica", "eletrônicos", "eletrodomésticos", "empório", "especialidades",
            "espaço", "esperança", "espírito", "estética", "eustáquio", "fábio", "família", "farmácia",
            "farmacêutico", "farmacêutica", "fátima", "fé", "funerária", "ginecológica", "glória", "gráfica",
            "guamá", "hidráulica", "imóveis", "indígena", "indústria", "informática", "inácio", "instalação",
            "irmão", "irmãos", "irmãs", "joão", "josé", "laboratório", "lotérica", "lúcia", "manutenção",
            "maranhão", "marajó", "márcio", "mecânica", "médica", "médico", "médicos", "móveis", "município",
            "nação", "nazaré", "odontológico", "odontológica", "ótica", "pães", "pão", "paixão", "paraíso",
            "patrícia", "peças", "pecuária", "perpétuo", "poço", "polícia", "presentes", "produção", "prótese",
            "próteses", "rações", "ração", "reabilitação", "refrigeração", "regulação", "relojoaria", "rogério",
            "romão", "rosário", "salão", "saúde", "são", "sebastião", "sérgio", "serviço", "serviços",
            "simão", "sítio", "sodré", "técnica", "técnico", "toxicológico", "união", "urgência", "variedades",
            "venâncio", "veículos", "veterinária", "veterinário", "vidraçaria", "vigilância", "sanitária",
            "vitória", "apolônio", "girão", "cirúrgica", "psicológica", "fisioterápica", "ortopédica",
            "clínico", "análises", "químicos", "agroquímicos", "higiênicos", "gás", "frangão", "atacadão",
            "mercadão", "lanchonete", "pizzaria", "sorveteria", "tapioca", "tacacá", "cupuaçu", "limão",
            "feijão", "sabão", "algodão", "botijão", "bujão", "leilão", "lavação", "habitação", "estação",
            "preço", "preços", "ótimo", "econômico", "econômica", "prático", "prática", "rápido", "rápida",
            "único", "única", "três", "irmã", "mamãe", "escritório", "consultório", "portões", "portão",
            "fábrica", "solução", "soluções", "motopeças", "autopeças", "contábil", "frigorífico", "picolé",
            "picolés", "óculos", "coração", "cirúrgico", "sítio", "música", "acessório", "calçado", "eletrônico",
            "lanchonetes", "distribuidora", "gás", "elétrico", "hidráulico", "mecânico", "pneumático", "vidros",
            "açougues", "mercadão", "verdão", "bolão", "baião", "pão", "limão", "sertão", "maranhão", "japão"
        };
        for (String palavra : palavras) {
            ACENTOS.put(TextoUtils.normalizar(palavra), palavra);
        }
    }

    private Capitalizacao() {
    }

    /**
     * Nome em formato de título. Nomes que já têm letras minúsculas (digitados
     * por pessoas, como no OpenStreetMap) só ganham a primeira letra maiúscula.
     */
    public static String nome(String original) {
        String texto = TextoUtils.limpar(original);
        if (texto == null) {
            return null;
        }
        texto = texto.replaceAll("\\s+", " ");
        if (!texto.equals(texto.toUpperCase(Locale.ROOT))) {
            return Character.toUpperCase(texto.charAt(0)) + texto.substring(1);
        }
        return titulo(texto);
    }

    /**
     * Endereço "Travessa Abdias Pereira, 230 - Tatajuba".
     *
     * @param logradouro rua com o tipo (ex.: "TRAV ABDIAS PEREIRA")
     * @param numero     número ("S/N", "0" e vazio viram "s/n")
     * @param bairro     bairro ou localidade (valores genéricos como "LOCALIDADE" são ignorados)
     */
    public static String endereco(String logradouro, String numero, String bairro) {
        String rua = TextoUtils.limpar(logradouro);
        if (rua == null) {
            return null;
        }
        String[] partes = rua.replaceAll("\\s+", " ").split(" ", 2);
        String tipo = LOGRADOUROS.get(partes[0].replace(".", "").toUpperCase(Locale.ROOT));
        String nomeRua = tipo != null && partes.length > 1 ? tipo + " " + titulo(partes[1]) : nome(rua);

        String num = TextoUtils.limpar(numero);
        String numeroFormatado = num == null || num.matches("(?i)s\\s*/?\\s*n|0+|sem numero|sem número")
                ? "s/n"
                : num.toUpperCase(Locale.ROOT).equals(num) ? num.toLowerCase(Locale.ROOT) : num;

        String resultado = nomeRua + ", " + numeroFormatado;
        String bairroFormatado = bairro(bairro);
        return bairroFormatado == null ? resultado : resultado + " - " + bairroFormatado;
    }

    private static String bairro(String bairro) {
        String texto = TextoUtils.limpar(bairro);
        if (texto == null) {
            return null;
        }
        String chave = TextoUtils.normalizar(texto);
        if (chave.equals("localidade") || chave.equals("comunidade") || chave.equals("sem bairro")
                || chave.equals("nao informado") || chave.equals("nao consta")) {
            return null;
        }
        if (chave.equals("zona rural") || chave.equals("rural")) {
            return "Zona rural";
        }
        return nome(texto);
    }

    /** Converte um texto em MAIÚSCULAS para o formato de título. */
    static String titulo(String texto) {
        List<String> saida = new ArrayList<>();
        String[] tokens = texto.strip().split("\\s+");
        for (int i = 0; i < tokens.length; i++) {
            String anterior = i > 0 ? TextoUtils.normalizar(tokens[i - 1]) : "";
            saida.add(palavra(tokens[i], i == 0, anterior));
        }
        return String.join(" ", saida);
    }

    private static String palavra(String token, boolean primeira, String anterior) {
        if (token.equalsIgnoreCase("S/A") || token.equalsIgnoreCase("S.A.")) {
            return token.toUpperCase(Locale.ROOT);
        }
        Matcher letras = PALAVRA.matcher(token);
        if (!letras.find()) {
            return token;
        }
        String nucleo = letras.group();
        String antes = token.substring(0, letras.start());
        String depois = token.substring(letras.end());
        return antes + formatarNucleo(nucleo, primeira, anterior) + formatarResto(depois);
    }

    /** O que vem depois da primeira palavra do token (ex.: "-124" em "PA-124", "/N" em "S/N"). */
    private static String formatarResto(String resto) {
        if (resto.isEmpty()) {
            return resto;
        }
        Matcher letras = PALAVRA.matcher(resto);
        StringBuilder saida = new StringBuilder();
        int posicao = 0;
        while (letras.find()) {
            saida.append(resto, posicao, letras.start()).append(formatarNucleo(letras.group(), false, ""));
            posicao = letras.end();
        }
        return saida.append(resto.substring(posicao)).toString();
    }

    private static String formatarNucleo(String nucleo, boolean primeira, String anterior) {
        String maiusculo = nucleo.toUpperCase(Locale.ROOT);
        String chave = TextoUtils.normalizar(nucleo);

        if (nucleo.chars().anyMatch(Character::isDigit)) {
            return maiusculo; // "PA-124", "CAPS1", "BOX16"
        }
        if (ABREVIACOES.containsKey(maiusculo)) {
            return ABREVIACOES.get(maiusculo);
        }
        if (SIGLAS.contains(maiusculo) || NUMERO_ROMANO.matcher(nucleo).matches() && !chave.equals("i") || semVogal(chave)) {
            return maiusculo;
        }
        if (!primeira && LIGACOES.contains(chave)) {
            return chave;
        }
        if (chave.length() == 1) {
            return maiusculo; // iniciais: "W M Prótese"
        }
        if (chave.length() == 2 && !PALAVRAS_CURTAS.contains(chave)) {
            return maiusculo; // iniciais: "EP", "JK"
        }
        if (chave.equals("para") && (anterior.equals("do") || anterior.equals("no"))) {
            return "Pará";
        }
        if (chave.equals("marco") && anterior.equals("de")) {
            return "Março";
        }
        String forma = ACENTOS.getOrDefault(chave, chave);
        return Character.toUpperCase(forma.charAt(0)) + forma.substring(1);
    }

    private static boolean semVogal(String palavra) {
        return palavra.length() <= 5 && palavra.chars().noneMatch(letra -> "aeiouy".indexOf(letra) >= 0);
    }
}
