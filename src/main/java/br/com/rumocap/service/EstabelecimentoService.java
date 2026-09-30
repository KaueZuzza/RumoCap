package br.com.rumocap.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.rumocap.dto.EstabelecimentoRequest;
import br.com.rumocap.dto.EstabelecimentoResponse;
import br.com.rumocap.dto.MarcadorResponse;
import br.com.rumocap.exception.RecursoNaoEncontradoException;
import br.com.rumocap.exception.RegraNegocioException;
import br.com.rumocap.model.Categoria;
import br.com.rumocap.model.Estabelecimento;
import br.com.rumocap.repository.CategoriaRepository;
import br.com.rumocap.repository.EstabelecimentoRepository;
import br.com.rumocap.util.TextoUtils;

@Service
@Transactional(readOnly = true)
public class EstabelecimentoService {

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ImagemService imagemService;

    public EstabelecimentoService(EstabelecimentoRepository estabelecimentoRepository,
                                  CategoriaRepository categoriaRepository,
                                  ImagemService imagemService) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.categoriaRepository = categoriaRepository;
        this.imagemService = imagemService;
    }

    /**
     * Lista os estabelecimentos em ordem alfabética.
     *
     * @param busca       texto procurado no nome, na descrição ou na categoria (sem diferenciar acentos)
     * @param categoriaId filtra por categoria quando informado
     */
    public List<EstabelecimentoResponse> listar(String busca, Long categoriaId) {
        String termo = TextoUtils.normalizar(busca);
        return estabelecimentoRepository.findAll().stream()
                .filter(estabelecimento -> pertenceACategoria(estabelecimento, categoriaId))
                .filter(estabelecimento -> termo.isEmpty() || correspondeABusca(estabelecimento, termo))
                .sorted(porNome())
                .map(EstabelecimentoResponse::de)
                .toList();
    }

    /** Estabelecimentos com latitude e longitude cadastradas, usados como marcadores do mapa. */
    public List<MarcadorResponse> listarMarcadores(Long categoriaId) {
        return estabelecimentoRepository.findByLatitudeIsNotNullAndLongitudeIsNotNull().stream()
                .filter(estabelecimento -> pertenceACategoria(estabelecimento, categoriaId))
                .sorted(porNome())
                .map(MarcadorResponse::de)
                .toList();
    }

    public EstabelecimentoResponse buscar(Long id) {
        return EstabelecimentoResponse.de(buscarEntidade(id));
    }

    @Transactional
    public EstabelecimentoResponse criar(EstabelecimentoRequest dados) {
        Estabelecimento estabelecimento = new Estabelecimento();
        preencher(estabelecimento, dados);
        return EstabelecimentoResponse.de(estabelecimentoRepository.save(estabelecimento));
    }

    @Transactional
    public EstabelecimentoResponse atualizar(Long id, EstabelecimentoRequest dados) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        preencher(estabelecimento, dados);
        return EstabelecimentoResponse.de(estabelecimento);
    }

    @Transactional
    public void excluir(Long id) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        String imagem = estabelecimento.getImagem();
        estabelecimentoRepository.delete(estabelecimento);
        estabelecimentoRepository.flush();
        imagemService.remover(imagem);
    }

    /** Adiciona ou substitui a imagem/logo do estabelecimento. */
    @Transactional
    public EstabelecimentoResponse atualizarImagem(Long id, MultipartFile arquivo) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        String imagemAnterior = estabelecimento.getImagem();
        String novaImagem = imagemService.salvar(arquivo);
        try {
            estabelecimento.setImagem(novaImagem);
            estabelecimentoRepository.flush();
        } catch (RuntimeException erro) {
            imagemService.remover(novaImagem);
            throw erro;
        }
        imagemService.remover(imagemAnterior);
        return EstabelecimentoResponse.de(estabelecimento);
    }

    @Transactional
    public EstabelecimentoResponse removerImagem(Long id) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        String imagemAnterior = estabelecimento.getImagem();
        estabelecimento.setImagem(null);
        estabelecimentoRepository.flush();
        imagemService.remover(imagemAnterior);
        return EstabelecimentoResponse.de(estabelecimento);
    }

    private Estabelecimento buscarEntidade(Long id) {
        return estabelecimentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado."));
    }

    private void preencher(Estabelecimento estabelecimento, EstabelecimentoRequest dados) {
        if ((dados.latitude() == null) != (dados.longitude() == null)) {
            throw new RegraNegocioException(
                    "Informe a latitude e a longitude juntas, ou deixe as duas em branco.");
        }
        Categoria categoria = categoriaRepository.findById(dados.categoriaId())
                .orElseThrow(() -> new RegraNegocioException("A categoria selecionada não existe."));

        estabelecimento.setNome(TextoUtils.limpar(dados.nome()));
        estabelecimento.setCategoria(categoria);
        estabelecimento.setDescricao(TextoUtils.limpar(dados.descricao()));
        estabelecimento.setEndereco(TextoUtils.limpar(dados.endereco()));
        estabelecimento.setContato(TextoUtils.limpar(dados.contato()));
        estabelecimento.setHorario(TextoUtils.limpar(dados.horario()));
        estabelecimento.setLatitude(dados.latitude());
        estabelecimento.setLongitude(dados.longitude());
    }

    private static boolean pertenceACategoria(Estabelecimento estabelecimento, Long categoriaId) {
        return categoriaId == null || categoriaId.equals(estabelecimento.getCategoria().getId());
    }

    private static boolean correspondeABusca(Estabelecimento estabelecimento, String termo) {
        return TextoUtils.normalizar(estabelecimento.getNome()).contains(termo)
                || TextoUtils.normalizar(estabelecimento.getDescricao()).contains(termo)
                || TextoUtils.normalizar(estabelecimento.getCategoria().getNome()).contains(termo);
    }

    private static Comparator<Estabelecimento> porNome() {
        return Comparator.comparing(Estabelecimento::getNome, TextoUtils.ordemAlfabetica());
    }
}
