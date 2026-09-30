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

@DisplayName("API de categorias")
class CategoriaApiTest extends ApiTestBase {

    @Test
    @DisplayName("lista as categorias em ordem alfabética, com \"Outros\" por último")
    void listaCategorias() throws Exception {
        DocumentContext resposta = json(mockMvc.perform(get("/api/categorias")).andExpect(status().isOk()));

        List<String> nomes = resposta.read("$[*].nome");
        assertThat(nomes).containsExactly(
                "Alimentação", "Automotivo", "Casa e Construção", "Mercados", "Moda e Beleza",
                "Saúde", "Serviços", "Tecnologia", "Outros");
        List<Integer> totais = resposta.read("$[*].totalEstabelecimentos");
        assertThat(totais).containsOnly(0);
    }

    @Test
    @DisplayName("exige login para criar categoria")
    void criarExigeLogin() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Educação\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    @DisplayName("recusa senha incorreta sem abrir a janela de login do navegador")
    void recusaSenhaIncorreta() throws Exception {
        mockMvc.perform(get("/api/admin/sessao").header(HttpHeaders.AUTHORIZATION, basic("admin", "errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));

        mockMvc.perform(get("/api/admin/sessao").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.usuario").value("admin"));
    }

    @Test
    @DisplayName("administrador cria, renomeia e exclui uma nova categoria")
    void cicloCompleto() throws Exception {
        DocumentContext criada = json(mockMvc.perform(post("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"  Educação  \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION)));
        long id = criada.read("$.id", Long.class);
        assertThat(criada.read("$.nome", String.class)).isEqualTo("Educação");

        DocumentContext renomeada = json(mockMvc.perform(put("/api/categorias/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"Educação e Cursos\"}"))
                .andExpect(status().isOk()));
        assertThat(renomeada.read("$.nome", String.class)).isEqualTo("Educação e Cursos");

        mockMvc.perform(delete("/api/categorias/{id}", id).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/categorias/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("não aceita nome repetido, mesmo sem acento ou com outras maiúsculas")
    void recusaNomeRepetido() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"SAUDE\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("valida o nome obrigatório")
    void validaNome() throws Exception {
        mockMvc.perform(post("/api/categorias")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nome\": \"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.nome").exists());
    }

    @Test
    @DisplayName("não exclui categoria que possui estabelecimentos")
    void naoExcluiCategoriaEmUso() throws Exception {
        long categoriaId = idDaCategoria("Outros");
        cadastrarEstabelecimento("{\"nome\": \"Registro de teste\", \"categoriaId\": " + categoriaId + "}");

        mockMvc.perform(delete("/api/categorias/{id}", categoriaId).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/categorias/{id}", categoriaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEstabelecimentos").value(1));
    }
}
