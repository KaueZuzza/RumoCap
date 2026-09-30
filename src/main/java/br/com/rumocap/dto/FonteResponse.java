package br.com.rumocap.dto;

/**
 * Fonte pública disponível para o levantamento.
 *
 * @param ultimaImportacao a execução mais recente (ou {@code null} se nunca foi importada)
 * @param cadastros        quantos estabelecimentos vieram desta fonte
 */
public record FonteResponse(String chave, String nome, String descricao, String licenca, String endereco,
                            ImportacaoResponse ultimaImportacao, long cadastros) {
}
