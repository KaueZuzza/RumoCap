package br.com.rumocap.levantamento.fontes;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.rumocap.levantamento.Candidato;
import br.com.rumocap.levantamento.Capitalizacao;
import br.com.rumocap.levantamento.Categorias;
import br.com.rumocap.levantamento.ClienteHttp;
import br.com.rumocap.levantamento.Coleta;
import br.com.rumocap.levantamento.FonteDeDados;
import br.com.rumocap.levantamento.FonteIndisponivelException;
import br.com.rumocap.levantamento.Municipio;
import br.com.rumocap.levantamento.Vocabulario;

/**
 * CNEFE - Cadastro Nacional de Endereços para Fins Estatísticos (IBGE, Censo 2022).
 * <p>
 * No Censo, cada endereço não residencial recebeu uma descrição anotada pelo
 * recenseador (ex.: "MERCADINHO BOM JESUS") e a coordenada de GPS do local.
 * Só entram as descrições com nome próprio: descrições genéricas ("BAR",
 * "DEPÓSITO"), locais vagos ou desativados e locais não comerciais (igrejas,
 * casas de farinha, currais) são descartados. Estabelecimentos de saúde vêm do CNES.
 */
@Component
@Order(3)
public class CnefeFonte implements FonteDeDados {

    private static final URI ARQUIVO = URI.create("https://ftp.ibge.gov.br/Cadastro_Nacional_de_Enderecos_para_Fins_Estatisticos/"
            + "Censo_Demografico_2022/Arquivos_CNEFE/CSV/Municipio/15_PA/1502301_CAPITAO_POCO.zip");
    private static final String PAGINA = "https://www.ibge.gov.br/estatisticas/sociais/populacao/"
            + "38734-cadastro-nacional-de-enderecos-para-fins-estatisticos.html";
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(120);

    /** Espécie 6: estabelecimento de outras finalidades (comércio, serviços...). */
    private static final String ESPECIE_OUTRAS_FINALIDADES = "6";
    /** Espécie 2: domicílio coletivo (entram só hotéis e pousadas). */
    private static final String ESPECIE_DOMICILIO_COLETIVO = "2";

    private static final Pattern HOSPEDAGEM = Pattern.compile("\\b(HOTEL|POUSADA|PENSAO|HOSPEDAGEM|DORMITORIO|MOTEL|HOSTEL)\\b");

    private static final Pattern FECHADO = Pattern.compile("\\b(VAGO|VAGA|VAGOS|VAZIO|VAZIA|DESATIVAD[OA]|FECHAD[OA]"
            + "|ABANDONAD[OA]|ANTIG[OA]|INATIV[OA]|DESOCUPAD[OA]|DESABITAD[OA]|EM CONSTRUCAO|EM OBRAS?|EM REFORMA"
            + "|SEM FUNCIONAMENTO|SEM USO|NAO FUNCIONA|ALUGA SE|VENDE SE)\\b");

    private static final Pattern NAO_COMERCIAL = Pattern.compile("\\b(IGREJA|ASSEMBLEIA|CONGREGACAO|TEMPLO|CAPELA"
            + "|PAROQUIA|PAROQUIAL|BATISTA|EVANGELICA|ADVENTISTA|CATOLICA|TESTEMUNHAS|CEMITERIO|ESCOLA(?! DE (?:INFORMATICA|IDIOMAS|INGLES|MUSICA|DANCA))"
            + "|CRECHE|ASSOCIACAO|SINDICATO|COOPERATIVA|COMUNITARI[OA]|SEDE|CAMPO DE (?:FUTEBOL|BOLA)|QUADRA DE ESPORTES"
            + "|QUADRA POLIESPORTIVA|ESTADIO|GINASIO|PRACA"
            + "|CASA DE (?:FAZER )?FARINHA|FAZER FARINHA|FARINHEIRA|RETIRO|CURRAL|GALINHEIRO|CHIQUEIRO|POCILGA|GRANJA|VIVEIRO"
            + "|APIARIO|ESTABULO|AVIARIO|CASA D[OA] POCO|POCO ARTESIANO|CASA D[AE] BOMBA|CAIXA D[AE]? ?AGUA|BANHEIROS?"
            + "|REFEITORIO|TERRENO|GARAGEM"
            + "|GALPAO|DEPOSITO(?! DE (?:BEBIDAS|GAS|MATERIA|CONSTRUCAO|MADEIRAS?|AREIA|TIJOLOS?|RACAO|RACOES|CEREAIS))"
            + "|BARRACAO|BARRACA|GIRAL|ABRIGO|CASA DE SAL|CAS(?:A|INHA) D[AEO]S? GALINHAS?|ESTRUTURA|OBRA|RUINA"
            + "|ESTUFA|SECADOR|TULHA|PAIOL|TANQUE|ACUDE|ORATORIO|HORTA|CHACARA|SITIO|FAZENDA|TELADO|PLANTACAO"
            + "|ROCA|PASTO|PREFEITURA|SECRETARIA|CAMARA MUNICIPAL|DELEGACIA|QUARTEL|POSTO DE SAUDE|UBS|USF"
            + "|RESIDENCIA|MORADIA|CASA DE MORADA|ALOJAMENTO|ALMOXARIFADO|CONSELHO|CRAS|CREAS|FORUM|DEFENSORIA|PROMOTORIA"
            + "|EMATER|ADEPARA|SEM NOME|NAO TEM|NAO INFORMADO|NAO IDENTIFICADO"
            + "|NAO SABE|SEM I?N?DENTIFICA\\w*|SEM DESCRICAO|IGNORADO|SINTINA|SENTINA|FOSSA)\\b");

    /** Palavras conhecidas usadas para corrigir erros de digitação ("MERACADINHO" -> "MERCADINHO"). */
    private static final List<String> VOCABULARIO = Vocabulario.paraCorrecao();

    private final ClienteHttp http;

    public CnefeFonte(ClienteHttp http) {
        this.http = http;
    }

    @Override
    public String chave() {
        return "cnefe";
    }

    @Override
    public String nome() {
        return "IBGE - CNEFE (Censo 2022)";
    }

    @Override
    public String descricao() {
        return "Endereços não residenciais visitados no Censo 2022, com a descrição anotada pelo recenseador e "
                + "a coordenada de GPS do local. Só entram descrições com nome próprio (ex.: \"Mercadinho Bom "
                + "Jesus\"); descrições genéricas, locais vagos ou desativados e locais não comerciais são "
                + "descartados. Os dados são de 2022: confirme se cada estabelecimento ainda funciona.";
    }

    @Override
    public String licenca() {
        return "IBGE, dados públicos do Censo Demográfico 2022";
    }

    @Override
    public String endereco() {
        return PAGINA;
    }

    @Override
    public Coleta coletar() {
        return interpretar(extrairCsv(http.obterBytes(ARQUIVO, TEMPO_LIMITE)));
    }

    /** O IBGE publica um .zip com um único arquivo .csv (separado por ponto e vírgula). */
    static String extrairCsv(byte[] zip) {
        try (ZipInputStream arquivo = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entrada = arquivo.getNextEntry(); entrada != null; entrada = arquivo.getNextEntry()) {
                if (entrada.getName().toLowerCase().endsWith(".csv")) {
                    return new String(arquivo.readAllBytes(), StandardCharsets.ISO_8859_1);
                }
            }
        } catch (IOException erro) {
            throw new FonteIndisponivelException("O arquivo do CNEFE baixado do IBGE está corrompido.", erro);
        }
        throw new FonteIndisponivelException("O arquivo do CNEFE baixado do IBGE não contém o CSV esperado.");
    }

    Coleta interpretar(String csv) {
        String[] linhas = csv.split("\\r?\\n");
        if (linhas.length < 2) {
            throw new FonteIndisponivelException("O arquivo do CNEFE está vazio.");
        }
        Map<String, Integer> colunas = new HashMap<>();
        String[] cabecalho = linhas[0].replace("﻿", "").split(";", -1);
        for (int i = 0; i < cabecalho.length; i++) {
            colunas.put(cabecalho[i].strip(), i);
        }
        for (String obrigatoria : List.of("COD_UNICO_ENDERECO", "COD_MUNICIPIO", "COD_ESPECIE", "DSC_ESTABELECIMENTO",
                "LATITUDE", "LONGITUDE", "NV_GEO_COORD", "NOM_SEGLOGR", "NUM_ENDERECO", "DSC_LOCALIDADE")) {
            if (!colunas.containsKey(obrigatoria)) {
                throw new FonteIndisponivelException("O arquivo do CNEFE mudou de formato: falta a coluna " + obrigatoria + ".");
            }
        }

        Coleta coleta = new Coleta();
        Set<String> vistos = new HashSet<>();
        for (int i = 1; i < linhas.length; i++) {
            if (linhas[i].isBlank()) {
                continue;
            }
            Linha linha = new Linha(linhas[i].split(";", -1), colunas);
            String especie = linha.valor("COD_ESPECIE");
            boolean hospedagem = false;
            if (!ESPECIE_OUTRAS_FINALIDADES.equals(especie)) {
                if (!ESPECIE_DOMICILIO_COLETIVO.equals(especie)
                        || !HOSPEDAGEM.matcher(Categorias.paraComparacao(linha.valor("DSC_ESTABELECIMENTO"))).find()) {
                    continue; // residências, escolas, igrejas e unidades de saúde não entram por aqui
                }
                hospedagem = true;
            }
            if (!Municipio.CODIGO_IBGE.equals(linha.valor("COD_MUNICIPIO"))) {
                coleta.descartar("De outro município");
                continue;
            }
            processar(linha, hospedagem, coleta, vistos);
        }
        return coleta;
    }

    private void processar(Linha linha, boolean hospedagem, Coleta coleta, Set<String> vistos) {
        String original = linha.valor("DSC_ESTABELECIMENTO");
        List<String> palavrasOriginais = List.of(Categorias.paraComparacao(original).split(" "));
        if (original.isBlank() || palavrasOriginais.isEmpty() || palavrasOriginais.get(0).isEmpty()) {
            coleta.descartar("Sem descrição no Censo");
            return;
        }
        List<String> palavras = palavrasOriginais.stream().map(CnefeFonte::corrigir).toList();
        String texto = String.join(" ", palavras);

        if (FECHADO.matcher(texto).find()) {
            coleta.descartar("Vago, fechado ou desativado no Censo");
            return;
        }
        if (!hospedagem && NAO_COMERCIAL.matcher(texto).find()) {
            coleta.descartar("Não é comércio ou serviço (igreja, depósito, casa de farinha, curral...)");
            return;
        }
        if (palavras.stream().noneMatch(CnefeFonte::nomeProprio)) {
            coleta.descartar("Descrição genérica, sem nome próprio (ex.: \"BAR\", \"MERCADINHO\")");
            return;
        }

        String codigo = linha.valor("COD_UNICO_ENDERECO");
        String fonteId = "cnefe:" + codigo + ":" + resumo(texto);
        if (!vistos.add(fonteId)) {
            coleta.descartar("Repetido no mesmo endereço do Censo");
            return;
        }

        String categoria = hospedagem ? Categorias.SERVICOS : Categorias.pelaDescricao(texto);
        Candidato candidato = new Candidato(fonteId, Capitalizacao.nome(texto),
                categoria == null ? Categorias.OUTROS : categoria);
        candidato.setUrlFonte(PAGINA);
        candidato.setTipoFonte(hospedagem
                ? "CNEFE espécie 2 (domicílio coletivo: hospedagem)"
                : "CNEFE espécie 6 (estabelecimento de outras finalidades)");
        candidato.setEndereco(endereco(linha));
        preencherLocalizacao(candidato, linha);

        candidato.observar("Registro do Censo 2022 (IBGE), anotado pelo recenseador. Confirme se o estabelecimento "
                + "ainda funciona e se o nome está correto.");
        if (!palavras.equals(palavrasOriginais)) {
            candidato.observar("Descrição original no Censo: \"" + original.strip() + "\" (erro de digitação corrigido).");
        }
        for (String coluna : List.of("COD_UNICO_ENDERECO", "DSC_ESTABELECIMENTO", "NOM_TIPO_SEGLOGR", "NOM_TITULO_SEGLOGR",
                "NOM_SEGLOGR", "NUM_ENDERECO", "DSC_MODIFICADOR", "DSC_LOCALIDADE", "CEP", "LATITUDE", "LONGITUDE",
                "NV_GEO_COORD", "COD_ESPECIE", "COD_SETOR")) {
            candidato.guardar(coluna, linha.valor(coluna));
        }
        coleta.adicionar(candidato);
    }

    private static String endereco(Linha linha) {
        String rua = String.join(" ", List.of(linha.valor("NOM_TIPO_SEGLOGR"), linha.valor("NOM_TITULO_SEGLOGR"),
                linha.valor("NOM_SEGLOGR"))).replaceAll("\\s+", " ").strip();
        if (rua.isEmpty()) {
            return null;
        }
        String numero = linha.valor("NUM_ENDERECO");
        String modificador = linha.valor("DSC_MODIFICADOR");
        if (modificador.toUpperCase().startsWith("KM")) {
            numero = (numero.isEmpty() || numero.equals("0") ? "" : numero + " ") + modificador.toLowerCase();
        }
        return Capitalizacao.endereco(rua, numero, linha.valor("DSC_LOCALIDADE"));
    }

    private static void preencherLocalizacao(Candidato candidato, Linha linha) {
        double latitude;
        double longitude;
        try {
            latitude = Double.parseDouble(linha.valor("LATITUDE").replace(',', '.'));
            longitude = Double.parseDouble(linha.valor("LONGITUDE").replace(',', '.'));
        } catch (NumberFormatException erro) {
            candidato.observar("O Censo não informa a coordenada deste endereço.");
            return;
        }
        String nivel = linha.valor("NV_GEO_COORD");
        if (!nivel.equals("1") && !nivel.equals("2")) {
            candidato.observar(switch (nivel) {
                case "3" -> "A coordenada deste endereço foi estimada pelo IBGE (não é do local) e foi descartada.";
                case "4" -> "A coordenada deste endereço é a da face da quadra (aproximada) e foi descartada.";
                case "5" -> "A coordenada deste endereço é a da localidade (genérica) e foi descartada.";
                default -> "A coordenada deste endereço é aproximada e foi descartada.";
            });
            return;
        }
        if (!Municipio.contem(latitude, longitude)) {
            candidato.observar("A coordenada informada fica fora do município e foi descartada.");
            return;
        }
        candidato.localizacao(latitude, longitude);
    }

    /** Palavra que faz parte de um nome próprio (não é genérica, número nem código de box). */
    private static boolean nomeProprio(String palavra) {
        return palavra.length() >= 2 && !Vocabulario.GENERICAS.contains(palavra) && !palavra.matches("\\d+|BOX\\d+");
    }

    /**
     * Troca por uma palavra conhecida quando a diferença é um erro de digitação
     * (uma letra em palavras de 6 a 9 letras; duas em palavras maiores).
     */
    static String corrigir(String palavra) {
        if (palavra.length() < 6 || Vocabulario.GENERICAS.contains(palavra) || palavra.chars().anyMatch(Character::isDigit)) {
            return palavra;
        }
        int limite = palavra.length() >= 10 ? 2 : 1;
        for (String conhecida : VOCABULARIO) {
            // singular e plural são palavras diferentes (SALGADO é sobrenome; SALGADOS, não)
            boolean plural = conhecida.equals(palavra + "S") || palavra.equals(conhecida + "S");
            if (!plural && Math.abs(conhecida.length() - palavra.length()) <= limite
                    && distancia(palavra, conhecida, limite) <= limite) {
                return conhecida;
            }
        }
        return palavra;
    }

    /** Distância de edição (com troca de letras vizinhas), interrompida ao passar do limite. */
    static int distancia(String a, String b, int limite) {
        int[][] d = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int menorDaLinha = Integer.MAX_VALUE;
            for (int j = 1; j <= b.length(); j++) {
                int custo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + custo);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    d[i][j] = Math.min(d[i][j], d[i - 2][j - 2] + 1);
                }
                menorDaLinha = Math.min(menorDaLinha, d[i][j]);
            }
            if (menorDaLinha > limite) {
                return limite + 1;
            }
        }
        return d[a.length()][b.length()];
    }

    private static String resumo(String texto) {
        CRC32 crc = new CRC32();
        crc.update(texto.getBytes(StandardCharsets.UTF_8));
        return String.format("%08x", crc.getValue());
    }

    /** Uma linha do CSV, com acesso às colunas pelo nome. */
    private record Linha(String[] valores, Map<String, Integer> colunas) {

        String valor(String coluna) {
            Integer indice = colunas.get(coluna);
            return indice == null || indice >= valores.length ? "" : valores[indice].strip();
        }
    }

}
