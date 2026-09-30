package br.com.rumocap.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.rumocap.model.Estabelecimento;
import br.com.rumocap.model.SituacaoRevisao;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {

    /** Carrega os estabelecimentos já com a categoria, em uma única consulta. */
    @Override
    @EntityGraph(attributePaths = "categoria")
    List<Estabelecimento> findAll();

    @Override
    @EntityGraph(attributePaths = "categoria")
    Optional<Estabelecimento> findById(Long id);

    /** Estabelecimentos visíveis no guia: aprovados e ativos. */
    @EntityGraph(attributePaths = "categoria")
    @Query("select e from Estabelecimento e where e.situacao = br.com.rumocap.model.SituacaoRevisao.APROVADO "
            + "and e.ativo = true")
    List<Estabelecimento> listarPublicos();

    @EntityGraph(attributePaths = "categoria")
    List<Estabelecimento> findBySituacao(SituacaoRevisao situacao);

    @EntityGraph(attributePaths = "categoria")
    List<Estabelecimento> findByIdIn(List<Long> ids);

    long countByCategoriaId(Long categoriaId);

    long countBySituacao(SituacaoRevisao situacao);

    long countBySituacaoAndAtivo(SituacaoRevisao situacao, boolean ativo);

    long countBySituacaoAndDuplicadoDeIdIsNotNull(SituacaoRevisao situacao);

    @Query("select count(e) from Estabelecimento e where e.situacao = br.com.rumocap.model.SituacaoRevisao.APROVADO "
            + "and e.ativo = true and (e.latitude is null or e.longitude is null)")
    long contarPublicosSemLocalizacao();

    /** Quantidade de estabelecimentos visíveis no guia, por categoria. */
    @Query("select e.categoria.id as categoriaId, count(e) as total from Estabelecimento e "
            + "where e.situacao = br.com.rumocap.model.SituacaoRevisao.APROVADO and e.ativo = true "
            + "group by e.categoria.id")
    List<TotalPorCategoria> contarPublicosPorCategoria();

    /** Quantidade de cadastros (em qualquer situação), por categoria. */
    @Query("select e.categoria.id as categoriaId, count(e) as total from Estabelecimento e group by e.categoria.id")
    List<TotalPorCategoria> contarTodosPorCategoria();

    interface TotalPorCategoria {

        Long getCategoriaId();

        Long getTotal();
    }
}
