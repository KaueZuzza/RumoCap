package br.com.rumocap.dto;

import br.com.rumocap.model.Categoria;

/** Categoria resumida, usada dentro das respostas de estabelecimentos. */
public record CategoriaResumo(Long id, String nome) {

    public static CategoriaResumo de(Categoria categoria) {
        return new CategoriaResumo(categoria.getId(), categoria.getNome());
    }
}
