package br.com.rumocap.model;

/**
 * Situação de um estabelecimento na revisão administrativa.
 * Somente os {@link #APROVADO aprovados} (e ativos) aparecem no guia público.
 */
public enum SituacaoRevisao {

    /** Encontrado em uma fonte pública e aguardando a conferência do administrador. */
    PENDENTE,

    /** Conferido pelo administrador (ou cadastrado por ele). */
    APROVADO,

    /**
     * Recusado na revisão. O registro é mantido para que a próxima importação
     * não traga o mesmo local de volta.
     */
    RECUSADO
}
