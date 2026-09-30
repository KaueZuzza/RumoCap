package br.com.rumocap;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import br.com.rumocap.model.Categoria;
import br.com.rumocap.repository.CategoriaRepository;
import br.com.rumocap.repository.EstabelecimentoRepository;
import br.com.rumocap.repository.ImportacaoRepository;
import br.com.rumocap.repository.RelatoRepository;

@DisplayName("Migrations do Flyway")
class MigracoesTest extends ApiTestBase {

    @Autowired
    private CategoriaRepository categoriaRepository;

    @Autowired
    private EstabelecimentoRepository estabelecimentoRepository;

    @Autowired
    private ImportacaoRepository importacaoRepository;

    @Autowired
    private RelatoRepository relatoRepository;

    @Test
    @DisplayName("criam somente as nove categorias iniciais")
    void criamAsCategoriasIniciais() {
        List<String> nomes = categoriaRepository.findAll().stream().map(Categoria::getNome).toList();

        assertThat(nomes).containsExactlyInAnyOrder(
                "Alimentação", "Saúde", "Mercados", "Moda e Beleza", "Tecnologia",
                "Serviços", "Casa e Construção", "Automotivo", "Outros");
    }

    @Test
    @DisplayName("não inserem nenhum estabelecimento fictício")
    void naoInseremEstabelecimentos() {
        assertThat(estabelecimentoRepository.count()).isZero();
    }

    @Test
    @DisplayName("criam as tabelas de importações e de avisos, vazias")
    void criamTabelasDoLevantamento() {
        assertThat(importacaoRepository.count()).isZero();
        assertThat(relatoRepository.count()).isZero();
    }
}
