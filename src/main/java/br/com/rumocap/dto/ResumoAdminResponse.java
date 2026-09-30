package br.com.rumocap.dto;

/**
 * Números do painel administrativo.
 *
 * @param pendentes             aguardando revisão
 * @param publicados            aprovados e ativos (visíveis no guia)
 * @param arquivados            aprovados, mas fora do guia
 * @param recusados             recusados na revisão
 * @param possiveisDuplicados   pendentes apontados como possível duplicado
 * @param publicadosSemLocalizacao visíveis no guia, mas sem posição no mapa
 * @param relatosAbertos        avisos de "informação incorreta" não resolvidos
 */
public record ResumoAdminResponse(long pendentes, long publicados, long arquivados, long recusados,
                                  long possiveisDuplicados, long publicadosSemLocalizacao, long relatosAbertos) {
}
