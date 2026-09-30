package br.com.rumocap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.DocumentContext;

@DisplayName("Informação incorreta? (avisos dos visitantes)")
class RelatoApiTest extends ApiTestBase {

    private static final String AVISO = "{\"mensagem\": \"O telefone informado não atende mais.\", \"contato\": \"\"}";

    @Test
    @DisplayName("qualquer visitante pode avisar, e a administração vê, resolve e exclui o aviso")
    void cicloDoAviso() throws Exception {
        long id = cadastrarEstabelecimento("{\"nome\": \"Registro de teste\", \"categoriaId\": " + idDaCategoria("Outros") + "}");

        mockMvc.perform(avisar(id, AVISO, "10.0.0.1")).andExpect(status().isCreated());

        DocumentContext avisos = json(mockMvc.perform(get("/api/admin/relatos").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk()));
        assertThat(avisos.read("$.length()", Integer.class)).isEqualTo(1);
        assertThat(avisos.read("$[0].estabelecimentoNome", String.class)).isEqualTo("Registro de teste");
        long relato = avisos.read("$[0].id", Long.class);

        mockMvc.perform(get("/api/admin/estabelecimentos/{id}", id).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.relatosAbertos").value(1));
        mockMvc.perform(get("/api/admin/resumo").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.relatosAbertos").value(1));

        mockMvc.perform(post("/api/admin/relatos/{id}/resolver", relato).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/admin/relatos").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/admin/relatos").param("resolvidos", "true").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$[0].resolvido").value(true));

        mockMvc.perform(delete("/api/admin/relatos/{id}", relato).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a lista de avisos exige login")
    void listaExigeLogin() throws Exception {
        mockMvc.perform(get("/api/admin/relatos")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("valida a mensagem e só aceita avisos de estabelecimentos do guia")
    void validacoes() throws Exception {
        long id = cadastrarEstabelecimento("{\"nome\": \"Registro de teste\", \"categoriaId\": " + idDaCategoria("Outros") + "}");

        mockMvc.perform(avisar(id, "{\"mensagem\": \"curta\"}", "10.0.0.2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.mensagem").exists());
        mockMvc.perform(avisar(999999, AVISO, "10.0.0.2")).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("envios de robôs (campo invisível preenchido) não são gravados")
    void armadilhaParaRobos() throws Exception {
        long id = cadastrarEstabelecimento("{\"nome\": \"Registro de teste\", \"categoriaId\": " + idDaCategoria("Outros") + "}");

        mockMvc.perform(avisar(id, "{\"mensagem\": \"Mensagem automática de robô\", \"site\": \"http://spam\"}", "10.0.0.3"))
                .andExpect(status().isCreated());
        mockMvc.perform(get("/api/admin/relatos").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("limita a quantidade de avisos seguidos da mesma origem")
    void limiteDeEnvios() throws Exception {
        long id = cadastrarEstabelecimento("{\"nome\": \"Registro de teste\", \"categoriaId\": " + idDaCategoria("Outros") + "}");
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(avisar(id, AVISO, "10.0.0.4")).andExpect(status().isCreated());
        }
        mockMvc.perform(avisar(id, AVISO, "10.0.0.4"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.mensagem").exists());
        mockMvc.perform(avisar(id, AVISO, "10.0.0.5")).andExpect(status().isCreated());
    }

    private static MockHttpServletRequestBuilder avisar(long id, String corpo, String origem) {
        return post("/api/estabelecimentos/{id}/relatos", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(corpo)
                .with(requisicao -> {
                    requisicao.setRemoteAddr(origem);
                    return requisicao;
                });
    }
}
