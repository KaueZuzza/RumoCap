package br.com.rumocap.levantamento;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Acesso HTTP às fontes públicas, com tempo limite para a importação nunca travar.
 * Nos testes automatizados é substituído por respostas gravadas.
 */
@Component
public class ClienteHttp {

    /** Identificação pedida pelas políticas de uso do OpenStreetMap e de outros serviços públicos. */
    private static final String IDENTIFICACAO = "RumoCap/1.0 (guia informativo de Capitao Poco; +https://github.com/KaueZuzza/RumoCap)";

    private final HttpClient cliente = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .sslContext(ConfiancaTls.contexto())
            .build();

    /** GET que devolve o corpo como texto (UTF-8). */
    public String obterTexto(URI endereco, Duration tempoLimite) {
        return new String(obterBytes(endereco, tempoLimite), StandardCharsets.UTF_8);
    }

    /** POST de formulário (usado pela API Overpass) que devolve o corpo como texto. */
    public String enviarFormulario(URI endereco, Map<String, String> campos, Duration tempoLimite) {
        StringBuilder corpo = new StringBuilder();
        campos.forEach((nome, valor) -> {
            if (!corpo.isEmpty()) {
                corpo.append('&');
            }
            corpo.append(java.net.URLEncoder.encode(nome, StandardCharsets.UTF_8))
                    .append('=')
                    .append(java.net.URLEncoder.encode(valor, StandardCharsets.UTF_8));
        });
        HttpRequest requisicao = HttpRequest.newBuilder(endereco)
                .timeout(tempoLimite)
                .header("User-Agent", IDENTIFICACAO)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(corpo.toString()))
                .build();
        return new String(executar(requisicao, endereco), StandardCharsets.UTF_8);
    }

    public byte[] obterBytes(URI endereco, Duration tempoLimite) {
        HttpRequest requisicao = HttpRequest.newBuilder(endereco)
                .timeout(tempoLimite)
                .header("User-Agent", IDENTIFICACAO)
                .header("Accept", "application/json, application/zip, */*")
                .GET()
                .build();
        return executar(requisicao, endereco);
    }

    private byte[] executar(HttpRequest requisicao, URI endereco) {
        try {
            HttpResponse<byte[]> resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofByteArray());
            if (resposta.statusCode() != 200) {
                throw new FonteIndisponivelException(
                        "O serviço " + endereco.getHost() + " respondeu com o código " + resposta.statusCode() + ".");
            }
            return resposta.body();
        } catch (IOException erro) {
            throw new FonteIndisponivelException(
                    "Não foi possível acessar " + endereco.getHost() + ": " + mensagem(erro), erro);
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new FonteIndisponivelException("A consulta a " + endereco.getHost() + " foi interrompida.", erro);
        }
    }

    private static String mensagem(IOException erro) {
        if (erro instanceof java.net.http.HttpTimeoutException) {
            return "o serviço demorou demais para responder.";
        }
        return erro.getMessage() == null ? erro.getClass().getSimpleName() : erro.getMessage();
    }
}
