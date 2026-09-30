package br.com.rumocap.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rumocap.dto.CategoriaRequest;
import br.com.rumocap.dto.CategoriaResponse;
import br.com.rumocap.exception.ConflitoException;
import br.com.rumocap.exception.RecursoNaoEncontradoException;
import br.com.rumocap.model.Categoria;
import br.com.rumocap.repository.CategoriaRepository;
import br.com.rumocap.repository.EstabelecimentoRepository;
import br.com.rumocap.repository.EstabelecimentoRepository.TotalPorCategoria;
import br.com.rumocap.util.TextoUtils;

@Service
@Transactional(readOnly = true)
public class CategoriaService {

    /** A categoria "Outros" é sempre exibida por último. */
    private static final String CATEGORIA_OUTROS = "outros";

    private final CategoriaRepository categoriaRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;

    public CategoriaService(CategoriaRepository categoriaRepository,
                            EstabelecimentoRepository estabelecimentoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
    }

    /** Categorias com a quantidade de estabelecimentos no guia e de cadastros (qualquer situação). */
    public List<CategoriaResponse> listar() {
        Map<Long, Long> publicos = totais(estabelecimentoRepository.contarPublicosPorCategoria());
        Map<Long, Long> cadastros = totais(estabelecimentoRepository.contarTodosPorCategoria());

        return categoriaRepository.findAll().stream()
                .sorted(ordemDeExibicao())
                .map(categoria -> CategoriaResponse.de(categoria, publicos.getOrDefault(categoria.getId(), 0L),
                        cadastros.getOrDefault(categoria.getId(), 0L)))
                .toList();
    }

    public CategoriaResponse buscar(Long id) {
        Categoria categoria = buscarEntidade(id);
        return resposta(categoria);
    }

    @Transactional
    public CategoriaResponse criar(CategoriaRequest dados) {
        String nome = TextoUtils.limpar(dados.nome());
        verificarNomeDisponivel(nome, null);
        Categoria categoria = categoriaRepository.save(new Categoria(nome));
        return CategoriaResponse.de(categoria, 0, 0);
    }

    @Transactional
    public CategoriaResponse atualizar(Long id, CategoriaRequest dados) {
        Categoria categoria = buscarEntidade(id);
        String nome = TextoUtils.limpar(dados.nome());
        verificarNomeDisponivel(nome, id);
        categoria.setNome(nome);
        return resposta(categoria);
    }

    @Transactional
    public void excluir(Long id) {
        Categoria categoria = buscarEntidade(id);
        long total = estabelecimentoRepository.countByCategoriaId(id);
        if (total > 0) {
            throw new ConflitoException("A categoria \"" + categoria.getNome() + "\" possui " + total
                    + " estabelecimento(s). Mova-os para outra categoria antes de excluí-la.");
        }
        categoriaRepository.delete(categoria);
    }

    private CategoriaResponse resposta(Categoria categoria) {
        long publicos = totais(estabelecimentoRepository.contarPublicosPorCategoria())
                .getOrDefault(categoria.getId(), 0L);
        return CategoriaResponse.de(categoria, publicos, estabelecimentoRepository.countByCategoriaId(categoria.getId()));
    }

    private static Map<Long, Long> totais(List<TotalPorCategoria> linhas) {
        return linhas.stream().collect(Collectors.toMap(TotalPorCategoria::getCategoriaId, TotalPorCategoria::getTotal));
    }

    private Categoria buscarEntidade(Long id) {
        return categoriaRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Categoria não encontrada."));
    }

    /** Impede nomes repetidos, ignorando acentos e maiúsculas ("Saúde" = "saude"). */
    private void verificarNomeDisponivel(String nome, Long idAtual) {
        String nomeNormalizado = TextoUtils.normalizar(nome);
        boolean repetido = categoriaRepository.findAll().stream()
                .filter(existente -> !existente.getId().equals(idAtual))
                .anyMatch(existente -> TextoUtils.normalizar(existente.getNome()).equals(nomeNormalizado));
        if (repetido) {
            throw new ConflitoException("Já existe uma categoria chamada \"" + nome + "\".");
        }
    }

    private static Comparator<Categoria> ordemDeExibicao() {
        return Comparator
                .comparing((Categoria categoria) -> CATEGORIA_OUTROS.equals(TextoUtils.normalizar(categoria.getNome())))
                .thenComparing(Categoria::getNome, TextoUtils.ordemAlfabetica());
    }
}
