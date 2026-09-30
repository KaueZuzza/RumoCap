package br.com.rumocap.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Dados enviados pela área administrativa para cadastrar ou editar um estabelecimento.
 * A imagem é enviada separadamente, em POST /api/estabelecimentos/{id}/imagem.
 *
 * @param contato             outros contatos (e-mail, redes sociais), um por linha
 * @param localizacaoValidada o administrador conferiu a posição no mapa
 */
public record EstabelecimentoRequest(

        @NotBlank(message = "Informe o nome do estabelecimento.")
        @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres.")
        String nome,

        @NotNull(message = "Selecione uma categoria.")
        Long categoriaId,

        @Size(max = 400, message = "A descrição deve ter no máximo 400 caracteres.")
        String descricao,

        @Size(max = 255, message = "O endereço deve ter no máximo 255 caracteres.")
        String endereco,

        @Size(max = 40, message = "O telefone deve ter no máximo 40 caracteres.")
        String telefone,

        @Size(max = 40, message = "O WhatsApp deve ter no máximo 40 caracteres.")
        String whatsapp,

        @Size(max = 255, message = "O site deve ter no máximo 255 caracteres.")
        String site,

        @Size(max = 500, message = "Os outros contatos devem ter no máximo 500 caracteres.")
        String contato,

        @Size(max = 500, message = "O horário deve ter no máximo 500 caracteres.")
        String horario,

        @DecimalMin(value = "-90.0", message = "A latitude deve estar entre -90 e 90.")
        @DecimalMax(value = "90.0", message = "A latitude deve estar entre -90 e 90.")
        Double latitude,

        @DecimalMin(value = "-180.0", message = "A longitude deve estar entre -180 e 180.")
        @DecimalMax(value = "180.0", message = "A longitude deve estar entre -180 e 180.")
        Double longitude,

        Boolean localizacaoValidada) {
}
