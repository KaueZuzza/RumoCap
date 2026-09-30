package br.com.rumocap.levantamento;

/**
 * Fonte pública consultada no levantamento de estabelecimentos.
 * Cada implementação só devolve informações que constam na fonte.
 */
public interface FonteDeDados {

    /** Identificador curto usado na API: "osm", "cnes" ou "cnefe". */
    String chave();

    /** Nome gravado no campo "fonte" dos estabelecimentos. */
    String nome();

    /** Explicação para o administrador: o que a fonte contém e seus limites. */
    String descricao();

    /** Licença ou condição de uso dos dados. */
    String licenca();

    /** Página de referência da fonte. */
    String endereco();

    /**
     * Consulta a fonte e devolve os candidatos já filtrados, formatados e categorizados.
     *
     * @throws FonteIndisponivelException quando a fonte não responde ou responde algo inesperado
     */
    Coleta coletar();
}
