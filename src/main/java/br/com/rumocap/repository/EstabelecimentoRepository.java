package br.com.rumocap.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.rumocap.model.Estabelecimento;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {

    /** Carrega os estabelecimentos já com a categoria, em uma única consulta. */
    @Override
    @EntityGraph(attributePaths = "categoria")
    List<Estabelecimento> findAll();

    @Override
    @EntityGraph(attributePaths = "categoria")
    Optional<Estabelecimento> findById(Long id);

    /** Somente os estabelecimentos que já têm localização cadastrada (usados no mapa). */
    @EntityGraph(attributePaths = "categoria")
    List<Estabelecimento> findByLatitudeIsNotNullAndLongitudeIsNotNull();

    long countByCategoriaId(Long categoriaId);

    @Query("select e.categoria.id as categoriaId, count(e) as total "
            + "from Estabelecimento e group by e.categoria.id")
    List<TotalPorCategoria> contarPorCategoria();

    interface TotalPorCategoria {

        Long getCategoriaId();

        Long getTotal();
    }
}
