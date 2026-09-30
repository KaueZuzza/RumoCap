package br.com.rumocap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Aviso de "informação incorreta" enviado na página do estabelecimento.
 *
 * @param contato opcional: e-mail ou telefone de quem avisou
 * @param site    campo invisível para pessoas (armadilha contra robôs): deve chegar vazio
 */
public record RelatoRequest(

        @NotBlank(message = "Conte o que está incorreto.")
        @Size(min = 10, max = 1000, message = "Escreva entre 10 e 1000 caracteres.")
        String mensagem,

        @Size(max = 120, message = "O contato deve ter no máximo 120 caracteres.")
        String contato,

        String site) {
}
