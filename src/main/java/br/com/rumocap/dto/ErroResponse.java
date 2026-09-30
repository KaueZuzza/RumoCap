package br.com.rumocap.dto;

import java.util.Map;

/**
 * Formato padrão dos erros da API.
 *
 * @param campos mensagens de validação por campo (vazio quando o erro não é de um campo específico)
 */
public record ErroResponse(int status, String mensagem, Map<String, String> campos) {

    public ErroResponse(int status, String mensagem) {
        this(status, mensagem, Map.of());
    }
}
