package br.com.rumocap.dto;

import br.com.rumocap.model.Categoria;

/**
 * @param totalEstabelecimentos estabelecimentos visíveis no guia (aprovados e ativos)
 * @param totalCadastros        cadastros em qualquer situação (inclui os aguardando revisão)
 */
public record CategoriaResponse(Long id, String nome, long totalEstabelecimentos, long totalCadastros) {

    public static CategoriaResponse de(Categoria categoria, long totalEstabelecimentos, long totalCadastros) {
        return new CategoriaResponse(categoria.getId(), categoria.getNome(), totalEstabelecimentos, totalCadastros);
    }
}
