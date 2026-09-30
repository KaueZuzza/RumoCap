package br.com.rumocap.dto;

import br.com.rumocap.model.Categoria;

public record CategoriaResponse(Long id, String nome, long totalEstabelecimentos) {

    public static CategoriaResponse de(Categoria categoria, long totalEstabelecimentos) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome(), totalEstabelecimentos);
    }
}
