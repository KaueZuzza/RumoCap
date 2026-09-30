package br.com.rumocap.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import br.com.rumocap.model.Importacao;

public interface ImportacaoRepository extends JpaRepository<Importacao, Long> {

    List<Importacao> findTop30ByOrderByIniciadaEmDesc();

    Optional<Importacao> findFirstByFonteOrderByIniciadaEmDesc(String fonte);

    Optional<Importacao> findFirstByFonteAndSituacaoOrderByIniciadaEmDesc(String fonte, Importacao.Situacao situacao);
}
