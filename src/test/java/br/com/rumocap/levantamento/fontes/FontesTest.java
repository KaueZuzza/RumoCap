package br.com.rumocap.levantamento.fontes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rumocap.levantamento.Candidato;
import br.com.rumocap.levantamento.Coleta;
import br.com.rumocap.levantamento.FonteIndisponivelException;
import tools.jackson.databind.JsonNode;

/**
 * As fontes são testadas com respostas reais gravadas em src/test/resources/levantamento
 * (OpenStreetMap, CNES e uma amostra de linhas do CNEFE de Capitão Poço).
 */
@DisplayName("Fontes públicas")
class FontesTest {

    /* ============================ OpenStreetMap ============================ */

    @Test
    @DisplayName("OpenStreetMap: só entram comércios e serviços com nome")
    void openStreetMap() throws IOException {
        Coleta coleta = new OpenStreetMapFonte(null).interpretar(recurso("osm-capitao-poco.json"));
        Map<String, Candidato> porNome = porNome(coleta);

        assertThat(porNome).containsKeys("DK Farma", "Click Enter - Provedor de Internet",
                "Oficina do cabelo barbershop", "Hotel João Moura", "Prefeitura Municipal de Capitão Poço",
                "Delegacia de Policia Civil");
        assertThat(porNome).doesNotContainKeys("Escola Chapeuzinho Vermelho", "Praça da Alvorada",
                "Igreja Nossa Senhora do Perpetuo Socorro", "Universidade Federal Rural da Amazônia - Campus Capitão Poço");
        assertThat(coleta.getDescartes()).isNotEmpty();
    }

    @Test
    @DisplayName("OpenStreetMap: aproveita endereço, telefone, horário e posição da fonte")
    void openStreetMapDetalhes() throws IOException {
        Candidato farmacia = porNome(new OpenStreetMapFonte(null).interpretar(recurso("osm-capitao-poco.json")))
                .get("DK Farma");

        assertThat(farmacia.getFonteId()).isEqualTo("osm:node/10659336051");
        assertThat(farmacia.getUrlFonte()).isEqualTo("https://www.openstreetmap.org/node/10659336051");
        assertThat(farmacia.getCategoria()).isEqualTo("Saúde");
        assertThat(farmacia.getDescricao()).isEqualTo("Farmácia");
        assertThat(farmacia.getEndereco()).isEqualTo("Travessa José Barros da Silva, 338 - Tatajuba");
        assertThat(farmacia.getTelefone()).isEqualTo("(91) 98578-5859");
        assertThat(farmacia.getWhatsapp()).isNull(); // a fonte não informa: nada é deduzido
        assertThat(farmacia.getHorario()).isEqualTo("Todos os dias: 07:00–22:00");
        assertThat(farmacia.getContato()).contains("drogariadkfarma@gmail.com");
        assertThat(farmacia.getLatitude()).isEqualTo(-1.7478191);
        assertThat(farmacia.getLongitude()).isEqualTo(-47.0638154);
    }

    @Test
    @DisplayName("OpenStreetMap: resposta inválida vira erro de fonte indisponível")
    void openStreetMapIndisponivel() {
        assertThatThrownBy(() -> new OpenStreetMapFonte(null).interpretar("<html>erro</html>"))
                .isInstanceOf(FonteIndisponivelException.class);
    }

    /* ================================= CNES ================================= */

    @Test
    @DisplayName("CNES: descarta desativados e classifica tudo como Saúde")
    void cnes() throws IOException {
        Coleta coleta = new CnesFonte(null).interpretar(registrosCnes());

        assertThat(coleta.getDescartes()).containsEntry("Desativado no CNES", 3);
        assertThat(coleta.getCandidatos()).hasSize(52)
                .allSatisfy(candidato -> assertThat(candidato.getCategoria()).isEqualTo("Saúde"));

        Candidato upa = porNome(coleta).get("Unidade de Pronto Atendimento UPA de Capitão Poço");
        assertThat(upa.getHorario()).startsWith("24 horas");
        assertThat(upa.getDescricao()).startsWith("Pronto atendimento");
        assertThat(upa.getUrlFonte()).endsWith("coUnidade=1502309481656");
        assertThat(upa.possuiLocalizacao()).isTrue();

        Candidato centro = porNome(coleta).get("Centro de Saúde de Capitão Poço");
        assertThat(centro.getTelefone()).isEqualTo("(91) 3468-1888");
        assertThat(centro.getEndereco()).isEqualTo("Travessa Abdias Pereira, 230 - Tatajuba");
    }

    @Test
    @DisplayName("CNES: coordenadas genéricas, imprecisas ou incoerentes viram localização pendente")
    void cnesCoordenadas() throws IOException {
        Map<String, Candidato> porNome = porNome(new CnesFonte(null).interpretar(registrosCnes()));

        // mesma coordenada para vários endereços diferentes (ponto genérico)
        assertThat(porNome.get("CAPS1 Rosa de Capitão Poço").possuiLocalizacao()).isFalse();
        assertThat(porNome.get("CAPS1 Rosa de Capitão Poço").getObservacoes())
                .anyMatch(observacao -> observacao.contains("ponto genérico"));
        // endereço rural com coordenada no centro da cidade
        assertThat(porNome.get("USF Vila Kenedy").possuiLocalizacao()).isFalse();
        // coordenada com três casas decimais
        assertThat(porNome.get("Pharmalab").possuiLocalizacao()).isFalse();
        // coordenada coerente é mantida
        assertThat(porNome.get("Hospital Dr. Aldomar Aarão Monteiro").possuiLocalizacao()).isTrue();
        assertThat(porNome.get("USF Vila de Sta. Luzia").possuiLocalizacao()).isTrue();
        // telefone incompleto não é aproveitado, mas fica registrado para a revisão
        Candidato secretaria = porNome.get("Secretaria Municipal de Saúde de Capitão Poço");
        assertThat(secretaria.getTelefone()).isNull();
        assertThat(secretaria.getObservacoes()).anyMatch(observacao -> observacao.contains("(09)14681491"));
    }

    /* ================================ CNEFE ================================ */

    @Test
    @DisplayName("CNEFE: só entram descrições com nome próprio de comércios e serviços")
    void cnefe() throws IOException {
        Coleta coleta = new CnefeFonte(null).interpretar(recurso("cnefe-amostra.csv"));
        Map<String, Candidato> porNome = porNome(coleta);

        assertThat(porNome).containsKeys("DS Pneus e Baterias", "Mercadinho Auricélio", "Cicinho Celular",
                "Posto de Gasolina EP", "Farmácia Pai Nosso", "Bar Ressaca", "Serralheria Laura",
                "Hotel Fazenda Cachoeira", "Zampa Juices");
        // genéricos, vagos, desativados e não comerciais ficam de fora
        assertThat(porNome.keySet()).noneMatch(nome -> List.of("Bar", "Deposito", "Vago", "Casa de Farinha",
                "Igreja Menino Jesus", "Antigo Bar", "Emporio do Frango", "Comercio de Alimentos",
                "Box16", "Assembleia de Deus", "Recanto das Lendas").contains(nome));
        assertThat(coleta.getDescartes().keySet()).anyMatch(motivo -> motivo.startsWith("Descrição genérica"));
        assertThat(coleta.getDescartes().keySet()).anyMatch(motivo -> motivo.startsWith("Vago"));
        assertThat(coleta.getDescartes().keySet()).anyMatch(motivo -> motivo.startsWith("Não é comércio"));
    }

    @Test
    @DisplayName("CNEFE: categoriza pelo nome, corrige erros de digitação e confere a coordenada")
    void cnefeDetalhes() throws IOException {
        Map<String, Candidato> porNome = porNome(new CnefeFonte(null).interpretar(recurso("cnefe-amostra.csv")));

        assertThat(porNome.get("DS Pneus e Baterias").getCategoria()).isEqualTo("Automotivo");
        assertThat(porNome.get("Mercadinho Auricélio").getCategoria()).isEqualTo("Mercados");
        assertThat(porNome.get("Cicinho Celular").getCategoria()).isEqualTo("Tecnologia");
        assertThat(porNome.get("Bar Ressaca").getCategoria()).isEqualTo("Alimentação");
        assertThat(porNome.get("Cris Bike Modas").getCategoria()).isEqualTo("Moda e Beleza");
        assertThat(porNome.get("Loja Nana Nene").getCategoria()).isEqualTo("Outros");
        assertThat(porNome.get("Hotel Fazenda Cachoeira").getCategoria()).isEqualTo("Serviços");

        Candidato meninas = porNome.get("Mercadinho das Meninas");
        assertThat(meninas).as("\"MERACADINHO\" corrigido para \"Mercadinho\"").isNotNull();
        assertThat(meninas.getObservacoes()).anyMatch(observacao -> observacao.contains("MERACADINHO DAS MENINAS"));

        Candidato pneus = porNome.get("DS Pneus e Baterias");
        assertThat(pneus.possuiLocalizacao()).isTrue();
        assertThat(pneus.getEndereco()).startsWith("Avenida João Moura da Costa, 1085");
        assertThat(pneus.getFonteId()).startsWith("cnefe:");

        Candidato variedades = porNome.get("Souza Variedades");
        assertThat(variedades.possuiLocalizacao()).as("coordenada de face de quadra é descartada").isFalse();
    }

    @Test
    @DisplayName("CNEFE: lê o CSV de dentro do .zip publicado pelo IBGE")
    void cnefeZip() throws IOException {
        byte[] csv = recurso("cnefe-amostra.csv").getBytes(StandardCharsets.ISO_8859_1);
        ByteArrayOutputStream zip = new ByteArrayOutputStream();
        try (ZipOutputStream saida = new ZipOutputStream(zip)) {
            saida.putNextEntry(new ZipEntry("1502301_CAPITAO_POCO.csv"));
            saida.write(csv);
            saida.closeEntry();
        }
        assertThat(CnefeFonte.extrairCsv(zip.toByteArray())).startsWith("COD_UNICO_ENDERECO;");
        assertThatThrownBy(() -> CnefeFonte.extrairCsv(new byte[] {1, 2, 3}))
                .isInstanceOf(FonteIndisponivelException.class);
    }

    @Test
    @DisplayName("CNEFE: corrige só erros de digitação, sem trocar sobrenomes por palavras parecidas")
    void correcaoDeDigitacao() {
        assertThat(CnefeFonte.corrigir("MERACADINHO")).isEqualTo("MERCADINHO");
        assertThat(CnefeFonte.corrigir("CABELELEIRO")).isEqualTo("CABELEIREIRO");
        assertThat(CnefeFonte.corrigir("SALGADO")).isEqualTo("SALGADO");
        assertThat(CnefeFonte.corrigir("MOREIRA")).isEqualTo("MOREIRA");
        assertThat(CnefeFonte.corrigir("CAZUZA")).isEqualTo("CAZUZA");
    }

    /* ------------------------------------------------------------------ */

    static String recurso(String nome) throws IOException {
        try (InputStream entrada = FontesTest.class.getResourceAsStream("/levantamento/" + nome)) {
            if (entrada == null) {
                throw new IOException("Recurso de teste não encontrado: " + nome);
            }
            byte[] bytes = entrada.readAllBytes();
            return new String(bytes, nome.endsWith(".csv") ? StandardCharsets.ISO_8859_1 : StandardCharsets.UTF_8);
        }
    }

    private static List<JsonNode> registrosCnes() throws IOException {
        return CnesFonte.lerPagina(recurso("cnes-capitao-poco.json"));
    }

    private static Map<String, Candidato> porNome(Coleta coleta) {
        return coleta.getCandidatos().stream()
                .collect(Collectors.toMap(Candidato::getNome, candidato -> candidato, (primeiro, segundo) -> primeiro));
    }
}
