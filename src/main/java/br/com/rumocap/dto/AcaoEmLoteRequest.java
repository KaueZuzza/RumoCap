package br.com.rumocap.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * Aprovação ou recusa de vários estabelecimentos de uma vez (revisão).
 *
 * @param acao "aprovar" ou "recusar"
 */
public record AcaoEmLoteRequest(

        @NotBlank(message = "Informe a ação: aprovar ou recusar.")
        String acao,

        @NotEmpty(message = "Selecione ao menos um estabelecimento.")
        @Size(max = 500, message = "Selecione no máximo 500 estabelecimentos por vez.")
        List<Long> ids) {
}
