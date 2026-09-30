package br.com.rumocap.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import br.com.rumocap.model.Relato;

public interface RelatoRepository extends JpaRepository<Relato, Long> {

    List<Relato> findByResolvidoOrderByCriadoEmDesc(boolean resolvido);

    long countByResolvido(boolean resolvido);

    /** Relatos em aberto por estabelecimento. */
    @Query("select r.estabelecimentoId as estabelecimentoId, count(r) as total from Relato r "
            + "where r.resolvido = false group by r.estabelecimentoId")
    List<AbertosPorEstabelecimento> contarAbertosPorEstabelecimento();

    interface AbertosPorEstabelecimento {

        Long getEstabelecimentoId();

        Long getTotal();
    }
}
