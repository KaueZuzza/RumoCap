package br.com.rumocap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.DocumentContext;

/**
 * Os registros usados aqui existem apenas durante cada teste (banco H2 em memória,
 * transação desfeita ao final). Nenhum dado é gravado no PostgreSQL.
 */
@DisplayName("API de estabelecimentos")
class EstabelecimentoApiTest extends ApiTestBase {

    @Test
    @DisplayName("começa sem nenhum estabelecimento cadastrado")
    void listaVazia() throws Exception {
        mockMvc.perform(get("/api/estabelecimentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/estabelecimentos/mapa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("exige login para cadastrar, editar e excluir")
    void operacoesDeEscritaExigemLogin() throws Exception {
        String corpo = "{\"nome\": \"Registro de teste\", \"categoriaId\": 1}";
        mockMvc.perform(post("/api/estabelecimentos").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/estabelecimentos/1").contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/estabelecimentos/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("cadastra um estabelecimento e devolve todos os dados")
    void cadastraEConsulta() throws Exception {
        long categoriaId = idDaCategoria("Serviços");

        DocumentContext criado = json(mockMvc.perform(post("/api/estabelecimentos")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome": "  Registro de teste  ",
                                  "categoriaId": %d,
                                  "descricao": "Descrição de teste",
                                  "endereco": "Endereço de teste",
                                  "contato": "Telefone: (91) 0000-0000\\nWhatsApp: (91) 90000-0000",
                                  "horario": "Seg. a sex.: 8h às 18h",
                                  "latitude": -1.7,
                                  "longitude": -47.0
                                }
                                """.formatted(categoriaId)))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION)));

        long id = criado.read("$.id", Long.class);
        DocumentContext consultado = json(mockMvc.perform(get("/api/estabelecimentos/{id}", id))
                .andExpect(status().isOk()));

        assertThat(consultado.read("$.nome", String.class)).isEqualTo("Registro de teste");
        assertThat(consultado.read("$.categoria.nome", String.class)).isEqualTo("Serviços");
        assertThat(consultado.read("$.descricao", String.class)).isEqualTo("Descrição de teste");
        assertThat(consultado.read("$.endereco", String.class)).isEqualTo("Endereço de teste");
        assertThat(consultado.read("$.contato", String.class)).contains("WhatsApp: (91) 90000-0000");
        assertThat(consultado.read("$.horario", String.class)).isEqualTo("Seg. a sex.: 8h às 18h");
        assertThat(consultado.read("$.latitude", Double.class)).isEqualTo(-1.7);
        assertThat(consultado.read("$.longitude", Double.class)).isEqualTo(-47.0);
        assertThat(consultado.read("$.imagemUrl", String.class)).isNull();
    }

    @Test
    @DisplayName("valida nome e categoria obrigatórios")
    void validaCamposObrigatorios() throws Exception {
        mockMvc.perform(post("/api/estabelecimentos")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists())
                .andExpect(jsonPath("$.campos.categoriaId").exists());
    }

    @Test
    @DisplayName("exige latitude e longitude juntas e dentro dos limites")
    void validaLocalizacao() throws Exception {
        long categoriaId = idDaCategoria("Outros");

        mockMvc.perform(post("/api/estabelecimentos")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Registro de teste\", \"categoriaId\": " + categoriaId
                                + ", \"latitude\": -1.7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").exists());

        mockMvc.perform(post("/api/estabelecimentos")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Registro de teste\", \"categoriaId\": " + categoriaId
                                + ", \"latitude\": 95, \"longitude\": -47}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.latitude").exists());
    }

    @Test
    @DisplayName("recusa categoria inexistente")
    void recusaCategoriaInexistente() throws Exception {
        mockMvc.perform(post("/api/estabelecimentos")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Registro de teste\", \"categoriaId\": 999999}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("pesquisa pelo nome sem diferenciar acentos e filtra por categoria")
    void pesquisaEFiltra() throws Exception {
        long alimentacao = idDaCategoria("Alimentação");
        long automotivo = idDaCategoria("Automotivo");
        cadastrarEstabelecimento("{\"nome\": \"Açaí Teste\", \"categoriaId\": " + alimentacao + "}");
        cadastrarEstabelecimento("{\"nome\": \"Oficina Teste\", \"categoriaId\": " + automotivo + "}");

        List<String> porNome = json(mockMvc.perform(get("/api/estabelecimentos").param("busca", "ACAI")))
                .read("$[*].nome");
        assertThat(porNome).containsExactly("Açaí Teste");

        List<String> porCategoria = json(mockMvc.perform(get("/api/estabelecimentos")
                .param("categoriaId", String.valueOf(automotivo)))).read("$[*].nome");
        assertThat(porCategoria).containsExactly("Oficina Teste");

        List<String> todos = json(mockMvc.perform(get("/api/estabelecimentos"))).read("$[*].nome");
        assertThat(todos).containsExactly("Açaí Teste", "Oficina Teste");
    }

    @Test
    @DisplayName("altera categoria e localização; o mapa mostra só quem tem coordenadas")
    void alteraCategoriaELocalizacao() throws Exception {
        long outros = idDaCategoria("Outros");
        long tecnologia = idDaCategoria("Tecnologia");
        long id = cadastrarEstabelecimento("{\"nome\": \"Registro de teste\", \"categoriaId\": " + outros + "}");
        cadastrarEstabelecimento("{\"nome\": \"Outro registro\", \"categoriaId\": " + outros + "}");

        mockMvc.perform(put("/api/estabelecimentos/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Registro de teste\", \"categoriaId\": " + tecnologia
                                + ", \"latitude\": -1.74, \"longitude\": -47.06}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoria.id").value(tecnologia))
                .andExpect(jsonPath("$.latitude").value(-1.74));

        mockMvc.perform(get("/api/estabelecimentos/mapa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].longitude").value(-47.06));

        // removendo a localização, o estabelecimento sai do mapa
        mockMvc.perform(put("/api/estabelecimentos/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Registro de teste\", \"categoriaId\": " + tecnologia + "}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/estabelecimentos/mapa"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("exclui um estabelecimento")
    void exclui() throws Exception {
        long id = cadastrarEstabelecimento("{\"nome\": \"Registro de teste\", \"categoriaId\": "
                + idDaCategoria("Outros") + "}");

        mockMvc.perform(delete("/api/estabelecimentos/{id}", id).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/estabelecimentos/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    @DisplayName("responde 404 para id inexistente e 400 para id inválido")
    void idsInvalidos() throws Exception {
        mockMvc.perform(get("/api/estabelecimentos/999999")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/estabelecimentos/abc")).andExpect(status().isBadRequest());
    }
}
