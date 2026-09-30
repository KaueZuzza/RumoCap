package br.com.rumocap.dto;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import br.com.rumocap.model.Importacao;

/** Resumo de uma execução do levantamento em uma fonte. */
public record ImportacaoResponse(
        Long id,
        String fonte,
        String situacao,
        Instant iniciadaEm,
        Instant concluidaEm,
        int encontrados,
        int novos,
        int atualizados,
        int semAlteracao,
        int possiveisDuplicados,
        int localizacaoPendente,
        int repetidos,
        int jaRecusados,
        int ignorados,
        List<String> detalhes,
        String mensagem) {

    public static ImportacaoResponse de(Importacao importacao) {
        List<String> detalhes = importacao.getDetalhes() == null
                ? List.of()
                : Arrays.stream(importacao.getDetalhes().split("\\n")).filter(linha -> !linha.isBlank()).toList();
        return new ImportacaoResponse(
                importacao.getId(),
                importacao.getFonte(),
                importacao.getSituacao().name(),
                importacao.getIniciadaEm(),
                importacao.getConcluidaEm(),
                importacao.getEncontrados(),
                importacao.getNovos(),
                importacao.getAtualizados(),
                importacao.getSemAlteracao(),
                importacao.getPossiveisDuplicados(),
                importacao.getLocalizacaoPendente(),
                importacao.getRepetidos(),
                importacao.getJaRecusados(),
                importacao.getIgnorados(),
                detalhes,
                importacao.getMensagem());
    }
}
