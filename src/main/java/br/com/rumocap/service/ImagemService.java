package br.com.rumocap.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import br.com.rumocap.config.RumoCapProperties;
import br.com.rumocap.exception.RegraNegocioException;

/**
 * Grava e remove as imagens/logos dos estabelecimentos.
 * <p>
 * O arquivo fica na pasta configurada em {@code rumocap.upload.diretorio} e o
 * PostgreSQL guarda apenas o nome do arquivo (coluna {@code imagem}).
 */
@Service
public class ImagemService {

    private static final Logger log = LoggerFactory.getLogger(ImagemService.class);
    private static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;

    private final Path diretorio;

    public ImagemService(RumoCapProperties propriedades) {
        this.diretorio = Path.of(propriedades.upload().diretorio()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(diretorio);
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível criar a pasta de imagens " + diretorio, erro);
        }
        log.info("Imagens dos estabelecimentos serão gravadas em {}", diretorio);
    }

    public Path getDiretorio() {
        return diretorio;
    }

    /**
     * Valida e grava a imagem enviada.
     *
     * @return nome do arquivo gerado (a ser salvo na coluna {@code imagem})
     */
    public String salvar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new RegraNegocioException("Selecione uma imagem para enviar.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new RegraNegocioException("A imagem deve ter no máximo 5 MB.");
        }

        String nomeArquivo = UUID.randomUUID() + identificarExtensao(arquivo);
        try (InputStream conteudo = arquivo.getInputStream()) {
            Files.copy(conteudo, diretorio.resolve(nomeArquivo));
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível gravar a imagem.", erro);
        }
        return nomeArquivo;
    }

    /** Apaga o arquivo de uma imagem que deixou de ser usada. */
    public void remover(String nomeArquivo) {
        if (nomeArquivo == null || nomeArquivo.isBlank()) {
            return;
        }
        Path arquivo = diretorio.resolve(nomeArquivo).normalize();
        if (!arquivo.startsWith(diretorio)) {
            return; // nunca apaga nada fora da pasta de uploads
        }
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException erro) {
            log.warn("Não foi possível apagar a imagem {}: {}", arquivo, erro.getMessage());
        }
    }

    /**
     * Descobre o formato pelo conteúdo do arquivo (e não pelo nome enviado),
     * aceitando somente PNG, JPG e WEBP.
     */
    private static String identificarExtensao(MultipartFile arquivo) {
        byte[] inicio;
        try (InputStream conteudo = arquivo.getInputStream()) {
            inicio = conteudo.readNBytes(12);
        } catch (IOException erro) {
            throw new UncheckedIOException("Não foi possível ler a imagem enviada.", erro);
        }

        if (comecaCom(inicio, 0x89, 'P', 'N', 'G')) {
            return ".png";
        }
        if (comecaCom(inicio, 0xFF, 0xD8, 0xFF)) {
            return ".jpg";
        }
        if (comecaCom(inicio, 'R', 'I', 'F', 'F') && inicio.length >= 12
                && inicio[8] == 'W' && inicio[9] == 'E' && inicio[10] == 'B' && inicio[11] == 'P') {
            return ".webp";
        }
        throw new RegraNegocioException("Formato não suportado. Envie uma imagem PNG, JPG ou WEBP.");
    }

    private static boolean comecaCom(byte[] dados, int... assinatura) {
        if (dados.length < assinatura.length) {
            return false;
        }
        for (int i = 0; i < assinatura.length; i++) {
            if ((dados[i] & 0xFF) != assinatura[i]) {
                return false;
            }
        }
        return true;
    }
}
