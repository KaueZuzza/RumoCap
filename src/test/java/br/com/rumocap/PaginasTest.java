package br.com.rumocap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

@DisplayName("Frontend e status")
class PaginasTest extends ApiTestBase {

    @Test
    @DisplayName("a raiz abre a página inicial")
    void raizAbreIndex() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/index.html", "/estabelecimentos.html", "/estabelecimento.html", "/mapa.html", "/admin.html",
            "/css/estilo.css", "/css/admin.css", "/js/api.js", "/js/comum.js", "/js/admin.js",
            "/js/tema.js", "/js/abertura.js", "/js/municipio.js", "/js/mapa.js",
            "/img/icones.svg", "/img/logo.svg", "/vendor/leaflet/leaflet.js"})
    @DisplayName("publica os arquivos do site")
    void publicaArquivos(String caminho) throws Exception {
        mockMvc.perform(get(caminho)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "/index.html", "/estabelecimentos.html", "/estabelecimento.html", "/mapa.html", "/admin.html"})
    @DisplayName("todas as páginas têm o seletor de modo claro/escuro")
    void paginasTemSeletorDeTema(String caminho) throws Exception {
        mockMvc.perform(get(caminho))
                .andExpect(content().string(Matchers.containsString("js/tema.js")))
                .andExpect(content().string(Matchers.containsString("data-alternar-tema")));
    }

    @Test
    @DisplayName("a página inicial tem tela de carregamento e apresentação")
    void paginaInicialTemAbertura() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(content().string(Matchers.containsString("id=\"abertura\"")))
                .andExpect(content().string(Matchers.containsString("id=\"apresentacao-continuar\"")))
                .andExpect(content().string(Matchers.containsString("id=\"apresentacao-ocultar\"")));
    }

    @Test
    @DisplayName("as páginas mostram o nome do projeto")
    void paginaInicial() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(content().string(Matchers.containsString("Rumo<span>Cap</span>")));
    }

    @Test
    @DisplayName("GET /api/status confirma API e banco")
    void statusDaApi() throws Exception {
        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.api").value("online"))
                .andExpect(jsonPath("$.banco").value("conectado"))
                .andExpect(jsonPath("$.categorias").value(9))
                .andExpect(jsonPath("$.estabelecimentos").value(0));
    }

    @Test
    @DisplayName("nenhum arquivo do frontend usa emojis")
    void semEmojis() throws IOException {
        Resource[] arquivos = new PathMatchingResourcePatternResolver().getResources("classpath:static/**/*.*");
        assertThat(arquivos).isNotEmpty();

        List<String> comEmoji = new ArrayList<>();
        for (Resource arquivo : arquivos) {
            String nome = arquivo.getFilename();
            if (nome == null || !nome.matches(".*\\.(html|css|js|svg)$")) {
                continue;
            }
            String conteudo = arquivo.getContentAsString(StandardCharsets.UTF_8);
            if (conteudo.codePoints().anyMatch(PaginasTest::ehEmoji)) {
                comEmoji.add(nome);
            }
        }
        assertThat(comEmoji).as("arquivos com emojis").isEmpty();
    }

    private static boolean ehEmoji(int caractere) {
        return (caractere >= 0x1F000 && caractere <= 0x1FAFF)
                || (caractere >= 0x2600 && caractere <= 0x27BF)
                || caractere == 0xFE0F;
    }
}
