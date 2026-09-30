package br.com.rumocap.dto;

import br.com.rumocap.levantamento.Duplicados.Semelhanca;
import br.com.rumocap.model.Estabelecimento;

/**
 * Cadastro semelhante (possível duplicado) e o motivo da suspeita.
 *
 * @param motivo explicação, por exemplo "nome 92% semelhante, a 35 m"
 */
public record SemelhanteResponse(Long id, String nome, String endereco, String fonte, String situacao,
                                 boolean ativo, String motivo) {

    public static SemelhanteResponse de(Semelhanca semelhanca) {
        Estabelecimento existente = semelhanca.existente();
        return new SemelhanteResponse(existente.getId(), existente.getNome(), existente.getEndereco(),
                existente.getFonte(), existente.getSituacao().name(), existente.isAtivo(), semelhanca.motivo());
    }

    public static SemelhanteResponse de(Estabelecimento existente, String motivo) {
        return new SemelhanteResponse(existente.getId(), existente.getNome(), existente.getEndereco(),
                existente.getFonte(), existente.getSituacao().name(), existente.isAtivo(), motivo);
    }
}
