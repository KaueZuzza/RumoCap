package br.com.rumocap.dto;

import java.time.Instant;

import br.com.rumocap.model.Relato;

public record RelatoResponse(Long id, Long estabelecimentoId, String estabelecimentoNome, String mensagem,
                             String contato, Instant criadoEm, boolean resolvido, Instant resolvidoEm) {

    public static RelatoResponse de(Relato relato, String estabelecimentoNome) {
        return new RelatoResponse(relato.getId(), relato.getEstabelecimentoId(), estabelecimentoNome,
                relato.getMensagem(), relato.getContato(), relato.getCriadoEm(), relato.isResolvido(),
                relato.getResolvidoEm());
    }
}
