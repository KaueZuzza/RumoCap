package br.com.rumocap.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import br.com.rumocap.model.Estabelecimento;

/**
 * Estabelecimento com todos os dados de controle, usado pela área administrativa.
 *
 * @param situacao                  PENDENTE (aguardando revisão), APROVADO ou RECUSADO
 * @param ativo                     falso quando arquivado
 * @param duplicadoDe               cadastro semelhante apontado pela importação
 * @param observacoes               alertas da importação (dados descartados, telefone incompleto...)
 * @param ausenteNaUltimaImportacao a fonte não trouxe mais este registro na importação mais recente
 * @param relatosAbertos            avisos de "informação incorreta" ainda não resolvidos
 */
public record EstabelecimentoAdminResponse(
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
        String imagemUrl,
        String situacao,
        boolean ativo,
        boolean localizacaoValidada,
        String fonte,
        String urlFonte,
        String fonteId,
        String tipoFonte,
        String dadosFonte,
        SemelhanteResponse duplicadoDe,
        List<String> observacoes,
        Instant criadoEm,
        Instant atualizadoEm,
        Instant vistoNaFonteEm,
        Instant revisadoEm,
        boolean ausenteNaUltimaImportacao,
        long relatosAbertos) {

    public static EstabelecimentoAdminResponse de(Estabelecimento estabelecimento, SemelhanteResponse duplicadoDe,
                                                  boolean ausenteNaUltimaImportacao, long relatosAbertos) {
        String observacao = estabelecimento.getObservacaoRevisao();
        List<String> observacoes = observacao == null || observacao.isBlank()
                ? List.of()
                : Arrays.stream(observacao.split("\\n")).map(String::strip).filter(texto -> !texto.isEmpty()).toList();
        return new EstabelecimentoAdminResponse(
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
                EstabelecimentoResponse.urlDaImagem(estabelecimento.getImagem()),
                estabelecimento.getSituacao().name(),
                estabelecimento.isAtivo(),
                estabelecimento.isLocalizacaoValidada(),
                estabelecimento.getFonte(),
                estabelecimento.getUrlFonte(),
                estabelecimento.getFonteId(),
                estabelecimento.getTipoFonte(),
                estabelecimento.getDadosFonte(),
                duplicadoDe,
                observacoes,
                estabelecimento.getCriadoEm(),
                estabelecimento.getAtualizadoEm(),
                estabelecimento.getVistoNaFonteEm(),
                estabelecimento.getRevisadoEm(),
                ausenteNaUltimaImportacao,
                relatosAbertos);
    }
}
