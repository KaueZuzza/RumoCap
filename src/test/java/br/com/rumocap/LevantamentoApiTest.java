package br.com.rumocap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.jayway.jsonpath.DocumentContext;

import br.com.rumocap.levantamento.ClienteHttp;
import br.com.rumocap.levantamento.FonteIndisponivelException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Importação das fontes públicas e revisão administrativa, de ponta a ponta:
 * Fonte -> validação e categorização -> banco (H2 dos testes) -> API.
 * <p>
 * As fontes respondem com dados reais gravados em src/test/resources/levantamento;
 * nenhuma chamada sai para a internet.
 */
@DisplayName("Levantamento e revisão de estabelecimentos")
class LevantamentoApiTest extends ApiTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @MockitoBean
    private ClienteHttp http;

    @BeforeEach
    void respostasGravadas() throws IOException {
        String osm = recurso("osm-capitao-poco.json");
        JsonNode cnes = JSON.readTree(recurso("cnes-capitao-poco.json")).path("estabelecimentos");
        byte[] cnefe = zip(recurso("cnefe-amostra.csv").getBytes(StandardCharsets.ISO_8859_1));

        when(http.enviarFormulario(any(), any(), any())).thenReturn(osm);
        when(http.obterBytes(any(), any())).thenReturn(cnefe);
        // a API do CNES é paginada (limit/offset)
        when(http.obterTexto(any(), any())).thenAnswer(chamada -> {
            URI endereco = chamada.getArgument(0);
            int offset = Integer.parseInt(endereco.getQuery().replaceAll(".*offset=(\\d+).*", "$1"));
            ArrayNode pagina = JSON.createArrayNode();
            for (int i = offset; i < Math.min(offset + 20, cnes.size()); i++) {
                pagina.add(cnes.get(i));
            }
            ObjectNode resposta = JSON.createObjectNode();
            resposta.set("estabelecimentos", pagina);
            return JSON.writeValueAsString(resposta);
        });
    }

    @Test
    @DisplayName("exige login para importar e para ver a revisão")
    void exigeLogin() throws Exception {
        mockMvc.perform(post("/api/admin/importacoes/osm")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/importacoes/fontes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/estabelecimentos")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/estabelecimentos/1/aprovar")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("lista as três fontes disponíveis")
    void fontes() throws Exception {
        List<String> chaves = json(mockMvc.perform(get("/api/admin/importacoes/fontes").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())).read("$[*].chave");
        assertThat(chaves).containsExactly("osm", "cnes", "cnefe");
    }

    @Test
    @DisplayName("importa do OpenStreetMap: tudo aguarda revisão e fica fora do guia público")
    void importaAguardandoRevisao() throws Exception {
        DocumentContext resultado = importar("osm");
        assertThat(resultado.read("$.situacao", String.class)).isEqualTo("CONCLUIDA");
        assertThat(resultado.read("$.novos", Integer.class)).isEqualTo(6);
        assertThat(resultado.read("$.ignorados", Integer.class)).isPositive();

        mockMvc.perform(get("/api/estabelecimentos")).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/estabelecimentos/mapa")).andExpect(jsonPath("$.length()").value(0));

        DocumentContext pendentes = pendentes();
        assertThat(pendentes.read("$.length()", Integer.class)).isEqualTo(6);
        assertThat(pendentes.read("$[*].fonte", List.class)).containsOnly("OpenStreetMap");
        assertThat(pendentes.read("$[*].situacao", List.class)).containsOnly("PENDENTE");

        long idFarmacia = idPeloNome(pendentes, "DK Farma");
        mockMvc.perform(get("/api/estabelecimentos/{id}", idFarmacia)).andExpect(status().isNotFound());

        DocumentContext farmacia = json(mockMvc.perform(get("/api/admin/estabelecimentos/{id}", idFarmacia)
                .header(HttpHeaders.AUTHORIZATION, ADMIN)));
        assertThat(farmacia.read("$.urlFonte", String.class)).isEqualTo("https://www.openstreetmap.org/node/10659336051");
        assertThat(farmacia.read("$.categoria.nome", String.class)).isEqualTo("Saúde");
        assertThat(farmacia.read("$.dadosFonte", String.class)).contains("\"amenity\":\"pharmacy\"");
        assertThat(farmacia.read("$.localizacaoValidada", Boolean.class)).isFalse();
    }

    @Test
    @DisplayName("aprovar publica no guia: a busca por \"farmácia\" encontra o local e o mapa mostra o marcador")
    void aprovarPublica() throws Exception {
        importar("osm");
        long id = idPeloNome(pendentes(), "DK Farma");

        mockMvc.perform(post("/api/admin/estabelecimentos/{id}/aprovar", id).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.situacao").value("APROVADO"));

        List<String> busca = json(mockMvc.perform(get("/api/estabelecimentos").param("busca", "farmácia"))).read("$[*].nome");
        assertThat(busca).containsExactly("DK Farma");
        List<String> porEndereco = json(mockMvc.perform(get("/api/estabelecimentos").param("busca", "barros da silva")))
                .read("$[*].nome");
        assertThat(porEndereco).containsExactly("DK Farma");

        mockMvc.perform(get("/api/estabelecimentos/mapa"))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].latitude").value(-1.7478191));
        mockMvc.perform(get("/api/estabelecimentos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.telefone").value("(91) 98578-5859"))
                .andExpect(jsonPath("$.fonte").value("OpenStreetMap"));
    }

    @Test
    @DisplayName("importar de novo não duplica e mantém as edições da administração")
    void reimportarNaoDuplica() throws Exception {
        importar("osm");
        long id = idPeloNome(pendentes(), "DK Farma");
        editarNome(id, "DK Farma Drogaria");

        DocumentContext segunda = importar("osm");
        assertThat(segunda.read("$.novos", Integer.class)).isZero();
        assertThat(segunda.read("$.encontrados", Integer.class)).isEqualTo(6);
        assertThat(pendentes().read("$.length()", Integer.class)).isEqualTo(6);
        mockMvc.perform(get("/api/admin/estabelecimentos/{id}", id).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.nome").value("DK Farma Drogaria"));
    }

    @Test
    @DisplayName("um local recusado não volta nas próximas importações")
    void recusadoNaoVolta() throws Exception {
        importar("osm");
        long id = idPeloNome(pendentes(), "Hotel João Moura");
        mockMvc.perform(post("/api/admin/estabelecimentos/{id}/recusar", id).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.situacao").value("RECUSADO"));

        DocumentContext segunda = importar("osm");
        assertThat(segunda.read("$.jaRecusados", Integer.class)).isEqualTo(1);
        assertThat(segunda.read("$.novos", Integer.class)).isZero();
        assertThat(pendentes().read("$.length()", Integer.class)).isEqualTo(5);
    }

    @Test
    @DisplayName("fonte fora do ar: a importação falha sem travar e sem alterar nada")
    void fonteIndisponivel() throws Exception {
        when(http.enviarFormulario(any(), any(), any()))
                .thenThrow(new FonteIndisponivelException("O serviço overpass-api.de respondeu com o código 504."));

        DocumentContext resultado = importar("osm");
        assertThat(resultado.read("$.situacao", String.class)).isEqualTo("FALHOU");
        assertThat(resultado.read("$.mensagem", String.class)).contains("504").contains("Nenhum dado foi alterado");
        assertThat(pendentes().read("$.length()", Integer.class)).isZero();
    }

    @Test
    @DisplayName("CNES: estabelecimentos de saúde com localização pendente quando a coordenada não é confiável")
    void importaCnes() throws Exception {
        DocumentContext resultado = importar("cnes");
        assertThat(resultado.read("$.novos", Integer.class)).isEqualTo(52);
        assertThat(resultado.read("$.localizacaoPendente", Integer.class)).isGreaterThanOrEqualTo(5);
        assertThat(resultado.read("$.detalhes", List.class)).contains("Desativado no CNES: 3");

        DocumentContext caps = json(mockMvc.perform(get("/api/admin/estabelecimentos")
                .param("busca", "CAPS1").header(HttpHeaders.AUTHORIZATION, ADMIN)));
        assertThat(caps.read("$[0].latitude", Double.class)).isNull();
        assertThat(caps.read("$[0].observacoes[*]", List.class).toString()).contains("ponto genérico");
    }

    @Test
    @DisplayName("CNEFE depois do CNES: repetições são ignoradas e o mesmo local em duas fontes vira possível duplicado")
    void duplicadosEntreFontes() throws Exception {
        importar("cnes");
        DocumentContext resultado = importar("cnefe");
        assertThat(resultado.read("$.novos", Integer.class)).isEqualTo(26);
        assertThat(resultado.read("$.repetidos", Integer.class)).isEqualTo(2);
        assertThat(resultado.read("$.possiveisDuplicados", Integer.class)).isEqualTo(2);

        DocumentContext duplicado = json(mockMvc.perform(get("/api/admin/estabelecimentos")
                .param("busca", "Dental Pro Dent").param("fonte", "cnefe").header(HttpHeaders.AUTHORIZATION, ADMIN)));
        assertThat(duplicado.read("$[0].duplicadoDe.nome", String.class)).isEqualTo("Pro Dent");
        assertThat(duplicado.read("$[0].duplicadoDe.motivo", String.class)).contains("mesmo nome");
    }

    @Test
    @DisplayName("mesclar completa o cadastro existente e recusa o duplicado")
    void mesclar() throws Exception {
        importar("cnes");
        importar("cnefe");
        DocumentContext lista = json(mockMvc.perform(get("/api/admin/estabelecimentos")
                .param("busca", "Clinica Reabilita").param("fonte", "cnefe").header(HttpHeaders.AUTHORIZATION, ADMIN)));
        long duplicado = lista.read("$[0].id", Long.class);
        long destino = lista.read("$[0].duplicadoDe.id", Long.class);

        mockMvc.perform(post("/api/admin/estabelecimentos/{id}/mesclar", duplicado).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(destino));
        mockMvc.perform(get("/api/admin/estabelecimentos/{id}", duplicado).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.situacao").value("RECUSADO"));
    }

    @Test
    @DisplayName("aprovação em lote, arquivamento e volta para a revisão")
    void loteEArquivamento() throws Exception {
        importar("osm");
        List<Integer> ids = pendentes().read("$[*].id");

        mockMvc.perform(post("/api/admin/estabelecimentos/lote").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\": \"aprovar\", \"ids\": " + ids + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alterados").value(6));
        mockMvc.perform(get("/api/estabelecimentos")).andExpect(jsonPath("$.length()").value(6));

        long id = ids.get(0);
        mockMvc.perform(post("/api/admin/estabelecimentos/{id}/arquivar", id).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.ativo").value(false));
        mockMvc.perform(get("/api/estabelecimentos/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/estabelecimentos")).andExpect(jsonPath("$.length()").value(5));

        mockMvc.perform(post("/api/admin/estabelecimentos/{id}/reativar", id).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.ativo").value(true));
        mockMvc.perform(post("/api/admin/estabelecimentos/{id}/revisar", id).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.situacao").value("PENDENTE"));
        mockMvc.perform(get("/api/estabelecimentos")).andExpect(jsonPath("$.length()").value(5));

        mockMvc.perform(get("/api/admin/resumo").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.pendentes").value(1))
                .andExpect(jsonPath("$.publicados").value(5));

        mockMvc.perform(post("/api/admin/estabelecimentos/lote").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"acao\": \"apagar\", \"ids\": [1]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("antes de um cadastro manual, avisa se já existe um local parecido")
    void verificarDuplicadosAntesDeCadastrar() throws Exception {
        importar("osm");
        DocumentContext semelhantes = json(mockMvc.perform(post("/api/admin/estabelecimentos/verificar-duplicados")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Drogaria DK Farma\", \"telefone\": \"(91) 98578-5859\"}"))
                .andExpect(status().isOk()));
        assertThat(semelhantes.read("$[0].nome", String.class)).isEqualTo("DK Farma");
        assertThat(semelhantes.read("$[0].motivo", String.class)).contains("mesmo telefone");
    }

    /* ------------------------------------------------------------------ */

    private DocumentContext importar(String fonte) throws Exception {
        return json(mockMvc.perform(post("/api/admin/importacoes/{fonte}", fonte).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk()));
    }

    private DocumentContext pendentes() throws Exception {
        return json(mockMvc.perform(get("/api/admin/estabelecimentos").param("situacao", "PENDENTE")
                .header(HttpHeaders.AUTHORIZATION, ADMIN)).andExpect(status().isOk()));
    }

    private static long idPeloNome(DocumentContext lista, String nome) {
        List<Integer> ids = lista.read("$[?(@.nome == '" + nome + "')].id");
        assertThat(ids).as("estabelecimento \"%s\" na lista", nome).hasSize(1);
        return ids.get(0);
    }

    private void editarNome(long id, String nome) throws Exception {
        DocumentContext atual = json(mockMvc.perform(get("/api/admin/estabelecimentos/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, ADMIN)));
        String corpo = """
                {"nome": "%s", "categoriaId": %d, "endereco": "%s", "telefone": "%s",
                 "latitude": %s, "longitude": %s, "localizacaoValidada": false}
                """.formatted(nome, atual.read("$.categoria.id", Long.class), atual.read("$.endereco", String.class),
                atual.read("$.telefone", String.class), atual.read("$.latitude", Double.class),
                atual.read("$.longitude", Double.class));
        mockMvc.perform(put("/api/estabelecimentos/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isOk());
    }

    static String recurso(String nome) throws IOException {
        try (InputStream entrada = LevantamentoApiTest.class.getResourceAsStream("/levantamento/" + nome)) {
            byte[] bytes = entrada.readAllBytes();
            return new String(bytes, nome.endsWith(".csv") ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
        }
    }

    private static byte[] zip(byte[] csv) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(saida)) {
            zip.putNextEntry(new ZipEntry("1502301_CAPITAO_POCO.csv"));
            zip.write(csv);
            zip.closeEntry();
        }
        return saida.toByteArray();
    }
}
