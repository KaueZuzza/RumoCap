package br.com.rumocap.dto;

import br.com.rumocap.model.Estabelecimento;

/** Dados mínimos para desenhar um estabelecimento no mapa. */
public record MarcadorResponse(
        Long id,
        String nome,
        CategoriaResumo categoria,
        String endereco,
        double latitude,
        double longitude) {

    public static MarcadorResponse de(Estabelecimento estabelecimento) {
        return new MarcadorResponse(
                estabelecimento.getId(),
                estabelecimento.getNome(),
                CategoriaResumo.de(estabelecimento.getCategoria()),
                estabelecimento.getEndereco(),
                estabelecimento.getLatitude(),
                estabelecimento.getLongitude());
    }
}
