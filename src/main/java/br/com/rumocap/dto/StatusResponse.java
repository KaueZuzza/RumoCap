package br.com.rumocap.dto;

/** Situação da aplicação, usada para verificar rapidamente se API e banco estão funcionando. */
public record StatusResponse(String api, String banco, Long categorias, Long estabelecimentos) {
}
