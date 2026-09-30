package br.com.rumocap.dto;

import br.com.rumocap.model.Estabelecimento;

public record EstabelecimentoResponse(
        Long id,
        String nome,
        CategoriaResumo categoria,
        String descricao,
        String endereco,
        String contato,
        String horario,
        Double latitude,
        Double longitude,
        String imagemUrl) {

    public static EstabelecimentoResponse de(Estabelecimento estabelecimento) {
        return new EstabelecimentoResponse(
                estabelecimento.getId(),
                estabelecimento.getNome(),
                CategoriaResumo.de(estabelecimento.getCategoria()),
                estabelecimento.getDescricao(),
                estabelecimento.getEndereco(),
                estabelecimento.getContato(),
                estabelecimento.getHorario(),
                estabelecimento.getLatitude(),
                estabelecimento.getLongitude(),
                urlDaImagem(estabelecimento.getImagem()));
    }

    static String urlDaImagem(String arquivo) {
        return arquivo == null ? null : "/uploads/" + arquivo;
    }
}
