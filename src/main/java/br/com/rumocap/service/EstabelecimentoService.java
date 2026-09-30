package br.com.rumocap.service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import br.com.rumocap.dto.EstabelecimentoRequest;
import br.com.rumocap.dto.EstabelecimentoResponse;
import br.com.rumocap.dto.MarcadorResponse;
import br.com.rumocap.exception.RecursoNaoEncontradoException;
import br.com.rumocap.exception.RegraNegocioException;
import br.com.rumocap.levantamento.Municipio;
import br.com.rumocap.levantamento.Telefones;
import br.com.rumocap.model.Categoria;
import br.com.rumocap.model.Estabelecimento;
import br.com.rumocap.model.SituacaoRevisao;
import br.com.rumocap.repository.CategoriaRepository;
import br.com.rumocap.repository.EstabelecimentoRepository;
import br.com.rumocap.util.Busca;
import br.com.rumocap.util.TextoUtils;

/**
 * Consultas do guia público (somente estabelecimentos aprovados e ativos) e
 * cadastro/edição feitos pela área administrativa.
 */
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
     * Lista os estabelecimentos do guia em ordem alfabética.
     *
     * @param busca       texto procurado no nome, na categoria, no endereço ou na descrição (sem diferenciar acentos)
     * @param categoriaId filtra por categoria quando informado
     */
    public List<EstabelecimentoResponse> listar(String busca, Long categoriaId) {
        Set<String> termos = Busca.alternativas(busca);
        return estabelecimentoRepository.listarPublicos().stream()
                .filter(estabelecimento -> pertenceACategoria(estabelecimento, categoriaId))
                .filter(estabelecimento -> termos.isEmpty() || correspondeABusca(estabelecimento, termos))
                .sorted(porNome())
                .map(EstabelecimentoResponse::de)
                .toList();
    }

    /** Estabelecimentos do guia com latitude e longitude, usados como marcadores do mapa. */
    public List<MarcadorResponse> listarMarcadores(Long categoriaId) {
        return estabelecimentoRepository.listarPublicos().stream()
                .filter(Estabelecimento::possuiLocalizacao)
                .filter(estabelecimento -> pertenceACategoria(estabelecimento, categoriaId))
                .sorted(porNome())
                .map(MarcadorResponse::de)
                .toList();
    }

    /** Página do estabelecimento: só existe para quem está no guia. */
    public EstabelecimentoResponse buscar(Long id) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        if (!estabelecimento.isPublico()) {
            throw new RecursoNaoEncontradoException("Estabelecimento não encontrado.");
        }
        return EstabelecimentoResponse.de(estabelecimento);
    }

    /** Cadastro manual: a administração é a fonte e o registro já entra aprovado. */
    @Transactional
    public Estabelecimento criar(EstabelecimentoRequest dados) {
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setFonte(Estabelecimento.FONTE_CADASTRO_MANUAL);
        estabelecimento.setSituacao(SituacaoRevisao.APROVADO);
        estabelecimento.setRevisadoEm(Instant.now());
        preencher(estabelecimento, dados);
        return estabelecimentoRepository.save(estabelecimento);
    }

    @Transactional
    public Estabelecimento atualizar(Long id, EstabelecimentoRequest dados) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        preencher(estabelecimento, dados);
        estabelecimento.marcarAtualizado();
        estabelecimentoRepository.flush();
        return estabelecimento;
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
    public Estabelecimento atualizarImagem(Long id, MultipartFile arquivo) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        String imagemAnterior = estabelecimento.getImagem();
        String novaImagem = imagemService.salvar(arquivo);
        try {
            estabelecimento.setImagem(novaImagem);
            estabelecimento.marcarAtualizado();
            estabelecimentoRepository.flush();
        } catch (RuntimeException erro) {
            imagemService.remover(novaImagem);
            throw erro;
        }
        imagemService.remover(imagemAnterior);
        return estabelecimento;
    }

    @Transactional
    public Estabelecimento removerImagem(Long id) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        String imagemAnterior = estabelecimento.getImagem();
        estabelecimento.setImagem(null);
        estabelecimento.marcarAtualizado();
        estabelecimentoRepository.flush();
        imagemService.remover(imagemAnterior);
        return estabelecimento;
    }

    Estabelecimento buscarEntidade(Long id) {
        return estabelecimentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado."));
    }

    private void preencher(Estabelecimento estabelecimento, EstabelecimentoRequest dados) {
        if ((dados.latitude() == null) != (dados.longitude() == null)) {
            throw new RegraNegocioException(
                    "Informe a latitude e a longitude juntas, ou deixe as duas em branco.");
        }
        if (dados.latitude() != null && !Municipio.contem(dados.latitude(), dados.longitude())) {
            throw new RegraNegocioException("A localização informada fica fora do município de Capitão Poço. "
                    + "Confira a latitude e a longitude.");
        }
        Categoria categoria = categoriaRepository.findById(dados.categoriaId())
                .orElseThrow(() -> new RegraNegocioException("A categoria selecionada não existe."));

        boolean mudouLocalizacao = !Objects.equals(estabelecimento.getLatitude(), dados.latitude())
                || !Objects.equals(estabelecimento.getLongitude(), dados.longitude());

        estabelecimento.setNome(TextoUtils.limpar(dados.nome()));
        estabelecimento.setCategoria(categoria);
        estabelecimento.setDescricao(TextoUtils.limpar(dados.descricao()));
        estabelecimento.setEndereco(TextoUtils.limpar(dados.endereco()));
        estabelecimento.setTelefone(telefone(dados.telefone(), "telefone"));
        estabelecimento.setWhatsapp(telefone(dados.whatsapp(), "WhatsApp"));
        estabelecimento.setSite(site(dados.site()));
        estabelecimento.setContato(TextoUtils.limpar(dados.contato()));
        estabelecimento.setHorario(TextoUtils.limpar(dados.horario()));
        estabelecimento.definirLocalizacao(dados.latitude(), dados.longitude());

        if (!estabelecimento.possuiLocalizacao()) {
            estabelecimento.setLocalizacaoValidada(false);
        } else if (dados.localizacaoValidada() != null) {
            estabelecimento.setLocalizacaoValidada(dados.localizacaoValidada());
        } else if (mudouLocalizacao) {
            // posição marcada pela própria administração (clientes da API sem o campo)
            estabelecimento.setLocalizacaoValidada(true);
        }
    }

    /** Telefone no formato "(91) 99999-9999"; números incompletos são recusados. */
    private static String telefone(String valor, String campo) {
        String texto = TextoUtils.limpar(valor);
        if (texto == null) {
            return null;
        }
        Telefones.Analise analise = Telefones.analisar(texto);
        if (!analise.valido()) {
            throw new RegraNegocioException("O " + campo + " \"" + texto + "\" parece incompleto. "
                    + "Use o formato (91) 99999-9999 ou (91) 3468-0000.");
        }
        return analise.formatado();
    }

    /** Site ou rede social: aceita "www.exemplo.com.br", "instagram.com/perfil" ou o endereço completo. */
    private static String site(String valor) {
        String texto = TextoUtils.limpar(valor);
        if (texto == null) {
            return null;
        }
        if (texto.contains(" ") || !texto.contains(".")) {
            throw new RegraNegocioException("Informe o endereço completo do site ou da rede social, "
                    + "por exemplo https://www.instagram.com/perfil.");
        }
        return texto.matches("(?i)https?://.+") ? texto : "https://" + texto;
    }

    private static boolean pertenceACategoria(Estabelecimento estabelecimento, Long categoriaId) {
        return categoriaId == null || categoriaId.equals(estabelecimento.getCategoria().getId());
    }

    /** Procura no nome, na categoria, no endereço e na descrição. */
    static boolean correspondeABusca(Estabelecimento estabelecimento, Set<String> termos) {
        String texto = TextoUtils.normalizar(String.join(" | ", estabelecimento.getNome(),
                estabelecimento.getCategoria().getNome(),
                Objects.toString(estabelecimento.getEndereco(), ""),
                Objects.toString(estabelecimento.getDescricao(), "")));
        return Busca.corresponde(texto, termos);
    }

    private static Comparator<Estabelecimento> porNome() {
        return Comparator.comparing(Estabelecimento::getNome, TextoUtils.ordemAlfabetica());
    }
}
