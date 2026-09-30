package br.com.rumocap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import br.com.rumocap.service.ImagemService;

@DisplayName("Imagens dos estabelecimentos")
class ImagemApiTest extends ApiTestBase {

    /** PNG de 1x1 pixel. */
    private static final byte[] PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==");

    /** Início de um arquivo JPEG (suficiente para a identificação do formato). */
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10,
            'J', 'F', 'I', 'F', 0, 1};

    @Autowired
    private ImagemService imagemService;

    @Test
    @DisplayName("adiciona, substitui e remove a imagem, apagando os arquivos antigos")
    void cicloDaImagem() throws Exception {
        long id = cadastrarEstabelecimento("{\"nome\": \"Registro de teste\", \"categoriaId\": "
                + idDaCategoria("Outros") + "}");

        String primeiraUrl = json(mockMvc.perform(multipart("/api/estabelecimentos/{id}/imagem", id)
                        .file(new MockMultipartFile("arquivo", "logo.png", "image/png", PNG))
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk()))
                .read("$.imagemUrl", String.class);

        assertThat(primeiraUrl).startsWith("/uploads/").endsWith(".png");
        Path primeiroArquivo = arquivoDa(primeiraUrl);
        assertThat(primeiroArquivo).exists();
        mockMvc.perform(get(primeiraUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(content().bytes(PNG));

        String segundaUrl = json(mockMvc.perform(multipart("/api/estabelecimentos/{id}/imagem", id)
                        .file(new MockMultipartFile("arquivo", "foto.jpg", "image/jpeg", JPEG))
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk()))
                .read("$.imagemUrl", String.class);

        assertThat(segundaUrl).endsWith(".jpg");
        assertThat(primeiroArquivo).doesNotExist();

        String semImagem = json(mockMvc.perform(delete("/api/estabelecimentos/{id}/imagem", id)
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk()))
                .read("$.imagemUrl", String.class);

        assertThat(semImagem).isNull();
        assertThat(arquivoDa(segundaUrl)).doesNotExist();
    }

    @Test
    @DisplayName("recusa arquivos que não são PNG, JPG ou WEBP")
    void recusaArquivoInvalido() throws Exception {
        long id = cadastrarEstabelecimento("{\"nome\": \"Registro de teste\", \"categoriaId\": "
                + idDaCategoria("Outros") + "}");
        byte[] texto = "isto não é uma imagem".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/estabelecimentos/{id}/imagem", id)
                        .file(new MockMultipartFile("arquivo", "falso.png", "image/png", texto))
                        .header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("exige login para enviar imagem")
    void envioExigeLogin() throws Exception {
        mockMvc.perform(multipart("/api/estabelecimentos/{id}/imagem", 1)
                        .file(new MockMultipartFile("arquivo", "logo.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    /** Caminho no disco do arquivo publicado em /uploads/{nome}. */
    private Path arquivoDa(String url) {
        return imagemService.getDiretorio().resolve(url.substring("/uploads/".length()));
    }
}
