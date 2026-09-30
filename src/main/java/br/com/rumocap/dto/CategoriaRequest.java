package br.com.rumocap.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoriaRequest(

        @NotBlank(message = "Informe o nome da categoria.")
        @Size(max = 80, message = "O nome da categoria deve ter no máximo 80 caracteres.")
        String nome) {
}
