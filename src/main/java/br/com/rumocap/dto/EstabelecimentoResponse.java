package br.com.rumocap.dto;

import java.time.Instant;

import br.com.rumocap.model.Estabelecimento;

/**
 * Estabelecimento como aparece no guia público.
 *
 * @param fonte               de onde vieram as informações ("OpenStreetMap", "Cadastro manual"...)
 * @param urlFonte            página do registro na fonte, quando existe
 * @param localizacaoValidada a posição no mapa foi conferida pela administração
 */
public record EstabelecimentoResponse(
        Long id,
        String nome,
        CategoriaResumo categoria,
        String descricao,
        String endereco,
        String telefone,
        String whatsapp,
        String site,
        String contato,
        String horario,
        Double latitude,
        Double longitude,
        boolean localizacaoValidada,
        String imagemUrl,
        String fonte,
        String urlFonte,
        Instant atualizadoEm) {

    public static EstabelecimentoResponse de(Estabelecimento estabelecimento) {
        return new EstabelecimentoResponse(
                estabelecimento.getId(),
                estabelecimento.getNome(),
                CategoriaResumo.de(estabelecimento.getCategoria()),
                estabelecimento.getDescricao(),
                estabelecimento.getEndereco(),
                estabelecimento.getTelefone(),
                estabelecimento.getWhatsapp(),
                estabelecimento.getSite(),
                estabelecimento.getContato(),
                estabelecimento.getHorario(),
                estabelecimento.getLatitude(),
                estabelecimento.getLongitude(),
                estabelecimento.isLocalizacaoValidada(),
                urlDaImagem(estabelecimento.getImagem()),
                estabelecimento.getFonte(),
                estabelecimento.getUrlFonte(),
                estabelecimento.getAtualizadoEm());
    }

    static String urlDaImagem(String arquivo) {
        return arquivo == null ? null : "/uploads/" + arquivo;
    }
}
