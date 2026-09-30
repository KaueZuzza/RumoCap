package br.com.rumocap;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

/**
 * Base dos testes de integração: sobe a aplicação completa com banco H2 em memória.
 * Cada teste roda em uma transação desfeita ao final, então nenhum dado permanece.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public abstract class ApiTestBase {

    protected static final String ADMIN = basic("admin", "senha-de-teste");

    @Autowired
    protected MockMvc mockMvc;

    protected static String basic(String usuario, String senha) {
        String credenciais = usuario + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(credenciais.getBytes(StandardCharsets.UTF_8));
    }

    /** Lê o corpo da resposta como JSON (em UTF-8, por causa dos acentos). */
    protected static DocumentContext json(ResultActions resultado) throws Exception {
        return JsonPath.parse(resultado.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** Cadastra um estabelecimento de teste pela API e devolve o id gerado. */
    protected long cadastrarEstabelecimento(String corpoJson) throws Exception {
        ResultActions resultado = mockMvc.perform(post("/api/estabelecimentos")
                        .header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpoJson))
                .andExpect(status().isCreated());
        return json(resultado).read("$.id", Long.class);
    }

    /** Id de uma categoria criada pela migration V2, procurada pelo nome. */
    protected long idDaCategoria(String nome) throws Exception {
        DocumentContext categorias = json(mockMvc.perform(get("/api/categorias")));
        return categorias.read("$[?(@.nome == '" + nome + "')].id", Long[].class)[0];
    }
}
