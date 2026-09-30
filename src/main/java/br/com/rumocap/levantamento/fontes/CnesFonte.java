package br.com.rumocap.levantamento.fontes;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.rumocap.levantamento.Candidato;
import br.com.rumocap.levantamento.Capitalizacao;
import br.com.rumocap.levantamento.Categorias;
import br.com.rumocap.levantamento.ClienteHttp;
import br.com.rumocap.levantamento.Coleta;
import br.com.rumocap.levantamento.FonteDeDados;
import br.com.rumocap.levantamento.FonteIndisponivelException;
import br.com.rumocap.levantamento.Horarios;
import br.com.rumocap.levantamento.Municipio;
import br.com.rumocap.levantamento.Similaridade;
import br.com.rumocap.levantamento.Telefones;
import br.com.rumocap.util.TextoUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CNES - Cadastro Nacional de Estabelecimentos de Saúde (Ministério da Saúde),
 * consultado pela API de dados abertos (apidadosabertos.saude.gov.br).
 * <p>
 * Traz hospitais, unidades básicas, clínicas, laboratórios e consultórios do
 * município. Estabelecimentos desativados no CNES são descartados, e as
 * coordenadas passam por conferências antes de serem usadas.
 */
@Component
@Order(2)
public class CnesFonte implements FonteDeDados {

    private static final String API = "https://apidadosabertos.saude.gov.br/cnes/estabelecimentos";
    private static final String FICHA = "https://cnes.datasus.gov.br/pages/estabelecimentos/ficha/index.jsp?coUnidade=";
    private static final int POR_PAGINA = 20;
    private static final int MAXIMO_DE_PAGINAS = 100;
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(45);

    /** Raio em que coordenadas iguais para endereços diferentes indicam um ponto genérico. */
    private static final double RAIO_PONTO_GENERICO = 15;

    private static final Pattern ENDERECO_RURAL = Pattern.compile(
            "^(vila|comunidade|assentamento|colonia|ramal|estrada|km|localidade|povoado|sitio|fazenda)\\b");

    /** Tipos de unidade do CNES (tabela oficial "Tipo de Estabelecimento"). */
    private static final Map<Integer, String> TIPOS = Map.ofEntries(
            Map.entry(1, "Posto de saúde"),
            Map.entry(2, "Centro de saúde / Unidade básica de saúde"),
            Map.entry(4, "Policlínica"),
            Map.entry(5, "Hospital geral"),
            Map.entry(7, "Hospital especializado"),
            Map.entry(15, "Unidade mista"),
            Map.entry(20, "Pronto-socorro geral"),
            Map.entry(21, "Pronto-socorro especializado"),
            Map.entry(22, "Consultório isolado"),
            Map.entry(36, "Clínica / centro de especialidade"),
            Map.entry(39, "Unidade de apoio diagnose e terapia (exames e terapias)"),
            Map.entry(40, "Unidade móvel terrestre"),
            Map.entry(42, "Unidade móvel de urgência"),
            Map.entry(43, "Farmácia"),
            Map.entry(50, "Unidade de vigilância em saúde"),
            Map.entry(61, "Centro de parto normal"),
            Map.entry(62, "Hospital-dia"),
            Map.entry(64, "Central de regulação de serviços de saúde"),
            Map.entry(68, "Central de gestão em saúde"),
            Map.entry(69, "Centro de hemoterapia e/ou hematologia"),
            Map.entry(70, "Centro de atenção psicossocial (CAPS)"),
            Map.entry(71, "Centro de apoio à saúde da família"),
            Map.entry(72, "Unidade de atenção à saúde indígena"),
            Map.entry(73, "Pronto atendimento"),
            Map.entry(74, "Polo Academia da Saúde"),
            Map.entry(75, "Telessaúde"),
            Map.entry(76, "Central de regulação médica das urgências"),
            Map.entry(77, "Serviço de atenção domiciliar"),
            Map.entry(79, "Oficina ortopédica"),
            Map.entry(80, "Laboratório de saúde pública"),
            Map.entry(81, "Central de regulação do acesso"),
            Map.entry(83, "Polo de prevenção de doenças e agravos"),
            Map.entry(84, "Central de abastecimento"),
            Map.entry(85, "Centro de imunização"));

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ClienteHttp http;

    public CnesFonte(ClienteHttp http) {
        this.http = http;
    }

    @Override
    public String chave() {
        return "cnes";
    }

    @Override
    public String nome() {
        return "CNES (Ministério da Saúde)";
    }

    @Override
    public String descricao() {
        return "Cadastro oficial dos estabelecimentos de saúde: hospitais, unidades básicas, clínicas, "
                + "laboratórios e consultórios, com endereço, telefone e turnos de atendimento. "
                + "Unidades desativadas no CNES são descartadas. Coordenadas repetidas, imprecisas "
                + "ou incoerentes com o endereço ficam como localização pendente.";
    }

    @Override
    public String licenca() {
        return "Dados abertos do Ministério da Saúde (DATASUS)";
    }

    @Override
    public String endereco() {
        return "https://cnes.datasus.gov.br/";
    }

    @Override
    public Coleta coletar() {
        List<JsonNode> registros = new ArrayList<>();
        for (int pagina = 0; pagina < MAXIMO_DE_PAGINAS; pagina++) {
            URI endereco = URI.create(API + "?codigo_municipio=" + Municipio.CODIGO_DATASUS
                    + "&limit=" + POR_PAGINA + "&offset=" + pagina * POR_PAGINA);
            List<JsonNode> lote = lerPagina(http.obterTexto(endereco, TEMPO_LIMITE));
            registros.addAll(lote);
            if (lote.size() < POR_PAGINA) {
                break;
            }
        }
        return interpretar(registros);
    }

    static List<JsonNode> lerPagina(String resposta) {
        try {
            JsonNode lista = JSON.readTree(resposta).path("estabelecimentos");
            if (!lista.isArray()) {
                throw new FonteIndisponivelException("A API do CNES devolveu uma resposta inesperada.");
            }
            return new ArrayList<>(lista.values());
        } catch (JacksonException erro) {
            throw new FonteIndisponivelException("A API do CNES devolveu uma resposta que não é JSON.", erro);
        }
    }

    Coleta interpretar(List<JsonNode> registros) {
        Coleta coleta = new Coleta();
        List<Candidato> candidatos = new ArrayList<>();
        List<double[]> pontosOriginais = new ArrayList<>();

        for (JsonNode registro : registros) {
            if (!texto(registro, "codigo_municipio").equals(Municipio.CODIGO_DATASUS)) {
                coleta.descartar("De outro município");
                continue;
            }
            if (!texto(registro, "codigo_motivo_desabilitacao_estabelecimento").isEmpty()) {
                coleta.descartar("Desativado no CNES");
                continue;
            }
            String nome = TextoUtils.limpar(texto(registro, "nome_fantasia"));
            if (nome == null) {
                nome = TextoUtils.limpar(texto(registro, "nome_razao_social"));
            }
            if (nome == null) {
                coleta.descartar("Sem nome no CNES");
                continue;
            }
            candidatos.add(candidato(registro, nome));
            pontosOriginais.add(pontoInformado(registro));
        }

        descartarPontosGenericos(candidatos, pontosOriginais);
        candidatos.forEach(coleta::adicionar);
        return coleta;
    }

    private Candidato candidato(JsonNode registro, String nome) {
        String codigo = texto(registro, "codigo_cnes");
        Candidato candidato = new Candidato("cnes:" + codigo, Capitalizacao.nome(nome), Categorias.SAUDE);

        String codigoCompleto = texto(registro, "codigo_estabelecimento_saude");
        candidato.setUrlFonte(FICHA + (codigoCompleto.isEmpty() ? Municipio.CODIGO_DATASUS + codigo : codigoCompleto));

        int tipo = registro.path("codigo_tipo_unidade").asInt(0);
        String descricaoTipo = TIPOS.getOrDefault(tipo, "Estabelecimento de saúde");
        candidato.setTipoFonte("CNES " + codigo + " - tipo " + tipo + ": " + descricaoTipo);
        boolean sus = "SIM".equalsIgnoreCase(texto(registro, "estabelecimento_faz_atendimento_ambulatorial_sus"));
        candidato.setDescricao(descricaoTipo + (sus ? ". Atendimento ambulatorial pelo SUS." : "."));

        candidato.setEndereco(Capitalizacao.endereco(texto(registro, "endereco_estabelecimento"),
                texto(registro, "numero_estabelecimento"), texto(registro, "bairro_estabelecimento")));
        candidato.setHorario(Horarios.deCnes(texto(registro, "descricao_turno_atendimento")));

        Telefones.Analise telefone = Telefones.analisar(texto(registro, "numero_telefone_estabelecimento"));
        if (telefone.valido()) {
            candidato.setTelefone(telefone.formatado());
        } else {
            candidato.observar(telefone.problema());
        }
        String email = TextoUtils.limpar(texto(registro, "endereco_email_estabelecimento"));
        if (email != null && email.contains("@")) {
            candidato.setContato("E-mail: " + email.toLowerCase(Locale.ROOT));
        }

        preencherLocalizacao(candidato, registro);

        for (String campo : List.of("codigo_cnes", "nome_fantasia", "nome_razao_social", "codigo_tipo_unidade",
                "endereco_estabelecimento", "numero_estabelecimento", "bairro_estabelecimento",
                "codigo_cep_estabelecimento", "numero_telefone_estabelecimento", "endereco_email_estabelecimento",
                "latitude_estabelecimento_decimo_grau", "longitude_estabelecimento_decimo_grau",
                "descricao_turno_atendimento", "estabelecimento_faz_atendimento_ambulatorial_sus",
                "descricao_esfera_administrativa", "data_atualizacao")) {
            JsonNode valor = registro.path(campo);
            if (!valor.isMissingNode() && !valor.isNull()) {
                candidato.guardar(campo, valor.isNumber() ? valor.numberValue() : valor.asString());
            }
        }
        String atualizacao = texto(registro, "data_atualizacao");
        if (!atualizacao.isEmpty()) {
            candidato.observar("Cadastro atualizado no CNES em " + dataBrasileira(atualizacao) + ".");
        }
        return candidato;
    }

    private static void preencherLocalizacao(Candidato candidato, JsonNode registro) {
        JsonNode lat = registro.path("latitude_estabelecimento_decimo_grau");
        JsonNode lng = registro.path("longitude_estabelecimento_decimo_grau");
        if (!lat.isNumber() || !lng.isNumber()) {
            candidato.observar("O CNES não informa a localização deste estabelecimento.");
            return;
        }
        double latitude = lat.asDouble();
        double longitude = lng.asDouble();
        String coordenada = coordenada(latitude, longitude);

        if (!Municipio.contem(latitude, longitude)) {
            candidato.observar("A coordenada informada ao CNES (" + coordenada + ") fica fora do município e foi descartada.");
            return;
        }
        if (poucasCasasDecimais(latitude) || poucasCasasDecimais(longitude)) {
            candidato.observar("A coordenada informada ao CNES (" + coordenada
                    + ") tem baixa precisão (erro de até 100 m) e foi descartada.");
            return;
        }
        String bairro = TextoUtils.normalizar(texto(registro, "bairro_estabelecimento"));
        String rua = TextoUtils.normalizar(texto(registro, "endereco_estabelecimento"));
        boolean rural = bairro.contains("rural") || bairro.equals("localidade") || bairro.equals("comunidade")
                || ENDERECO_RURAL.matcher(rua).find();
        if (rural && Municipio.distanciaDoCentro(latitude, longitude) < 2500) {
            candidato.observar("O endereço fica na zona rural, mas a coordenada informada ao CNES (" + coordenada
                    + ") está no centro da cidade. A coordenada foi descartada.");
            return;
        }
        candidato.localizacao(latitude, longitude);
    }

    /** Coordenada informada ao CNES, antes das conferências (usada para achar pontos genéricos). */
    private static double[] pontoInformado(JsonNode registro) {
        JsonNode lat = registro.path("latitude_estabelecimento_decimo_grau");
        JsonNode lng = registro.path("longitude_estabelecimento_decimo_grau");
        return lat.isNumber() && lng.isNumber() ? new double[] {lat.asDouble(), lng.asDouble()} : null;
    }

    /**
     * Quando vários estabelecimentos com endereços diferentes recebem praticamente
     * a mesma coordenada, ela é um ponto genérico (e não a localização real).
     */
    private static void descartarPontosGenericos(List<Candidato> candidatos, List<double[]> pontos) {
        int total = candidatos.size();
        int[] vizinhos = new int[total];
        for (int i = 0; i < total; i++) {
            for (int j = i + 1; j < total; j++) {
                if (pontos.get(i) == null || pontos.get(j) == null) {
                    continue;
                }
                double distancia = Municipio.distanciaEmMetros(pontos.get(i)[0], pontos.get(i)[1],
                        pontos.get(j)[0], pontos.get(j)[1]);
                boolean enderecosDiferentes = Similaridade.enderecos(
                        candidatos.get(i).getEndereco(), candidatos.get(j).getEndereco()) < 0.8;
                if (distancia <= RAIO_PONTO_GENERICO && enderecosDiferentes) {
                    vizinhos[i]++;
                    vizinhos[j]++;
                }
            }
        }
        for (int i = 0; i < total; i++) {
            Candidato candidato = candidatos.get(i);
            if (vizinhos[i] >= 2 && candidato.possuiLocalizacao()) {
                candidato.descartarLocalizacao("A coordenada informada ao CNES ("
                        + coordenada(candidato.getLatitude(), candidato.getLongitude()) + ") se repete em "
                        + (vizinhos[i] + 1) + " estabelecimentos com endereços diferentes; provavelmente é um "
                        + "ponto genérico. Confira a localização.");
            }
        }
    }

    private static boolean poucasCasasDecimais(double valor) {
        double escalado = valor * 1000;
        return Math.abs(escalado - Math.rint(escalado)) < 1e-6;
    }

    private static String coordenada(double latitude, double longitude) {
        return String.format(Locale.ROOT, "%.6f, %.6f", latitude, longitude);
    }

    private static String dataBrasileira(String iso) {
        String[] partes = iso.split("-");
        return partes.length == 3 ? partes[2] + "/" + partes[1] + "/" + partes[0] : iso;
    }

    private static String texto(JsonNode registro, String campo) {
        JsonNode valor = registro.path(campo);
        return valor.isMissingNode() || valor.isNull() ? "" : valor.asString().strip();
    }
}
