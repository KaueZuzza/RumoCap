package br.com.rumocap.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rumocap.dto.EstabelecimentoAdminResponse;
import br.com.rumocap.dto.EstabelecimentoRequest;
import br.com.rumocap.dto.ResumoAdminResponse;
import br.com.rumocap.dto.SemelhanteResponse;
import br.com.rumocap.exception.RecursoNaoEncontradoException;
import br.com.rumocap.exception.RegraNegocioException;
import br.com.rumocap.levantamento.Duplicados;
import br.com.rumocap.levantamento.Duplicados.Perfil;
import br.com.rumocap.levantamento.ImportacaoService;
import br.com.rumocap.model.Estabelecimento;
import br.com.rumocap.model.Importacao;
import br.com.rumocap.model.SituacaoRevisao;
import br.com.rumocap.repository.EstabelecimentoRepository;
import br.com.rumocap.repository.ImportacaoRepository;
import br.com.rumocap.repository.RelatoRepository;
import br.com.rumocap.repository.RelatoRepository.AbertosPorEstabelecimento;
import br.com.rumocap.util.Busca;
import br.com.rumocap.util.TextoUtils;

/**
 * Revisão administrativa: conferir os locais encontrados nas fontes públicas,
 * aprovar, recusar, arquivar, mesclar duplicados e acompanhar as pendências.
 */
@Service
@Transactional(readOnly = true)
public class RevisaoService {

    /** Filtro "ARQUIVADO": aprovados que saíram do guia. "PUBLICADO": aprovados e ativos. */
    public static final String ARQUIVADO = "ARQUIVADO";
    public static final String PUBLICADO = "PUBLICADO";

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            .withZone(ZoneId.of("America/Belem"));

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final ImportacaoRepository importacaoRepository;
    private final RelatoRepository relatoRepository;

    public RevisaoService(EstabelecimentoRepository estabelecimentoRepository,
                          ImportacaoRepository importacaoRepository,
                          RelatoRepository relatoRepository) {
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.importacaoRepository = importacaoRepository;
        this.relatoRepository = relatoRepository;
    }

    /**
     * Lista para a administração.
     *
     * @param situacao    PENDENTE, APROVADO, RECUSADO, PUBLICADO, ARQUIVADO ou vazio (todos)
     * @param busca       nome, endereço ou telefone
     * @param categoriaId categoria
     * @param fonte       "osm", "cnes", "cnefe" ou "manual"
     */
    public List<EstabelecimentoAdminResponse> listar(String situacao, String busca, Long categoriaId, String fonte) {
        Set<String> termos = Busca.alternativas(busca);
        String filtroSituacao = TextoUtils.limpar(situacao) == null ? null : situacao.strip().toUpperCase();
        List<Estabelecimento> lista = (filtroSituacao != null && !filtroSituacao.equals(PUBLICADO)
                && !filtroSituacao.equals(ARQUIVADO)
                ? estabelecimentoRepository.findBySituacao(situacao(filtroSituacao))
                : estabelecimentoRepository.findAll()).stream()
                .filter(estabelecimento -> filtroSituacao == null || atendeSituacao(estabelecimento, filtroSituacao))
                .filter(estabelecimento -> categoriaId == null || categoriaId.equals(estabelecimento.getCategoria().getId()))
                .filter(estabelecimento -> fonte == null || fonte.isBlank() || daFonte(estabelecimento, fonte))
                .filter(estabelecimento -> termos.isEmpty() || corresponde(estabelecimento, termos))
                .sorted(Comparator.comparing(Estabelecimento::getNome, TextoUtils.ordemAlfabetica()))
                .toList();
        return respostas(lista, false);
    }

    public EstabelecimentoAdminResponse buscar(Long id) {
        return respostas(List.of(buscarEntidade(id)), true).get(0);
    }

    public EstabelecimentoAdminResponse resposta(Estabelecimento estabelecimento) {
        return respostas(List.of(estabelecimento), true).get(0);
    }

    @Transactional
    public EstabelecimentoAdminResponse aprovar(Long id) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        aprovar(estabelecimento);
        return resposta(estabelecimento);
    }

    @Transactional
    public EstabelecimentoAdminResponse recusar(Long id) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        recusar(estabelecimento);
        return resposta(estabelecimento);
    }

    /** Devolve para a fila de revisão (sai do guia até ser aprovado de novo). */
    @Transactional
    public EstabelecimentoAdminResponse voltarParaRevisao(Long id) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        estabelecimento.setSituacao(SituacaoRevisao.PENDENTE);
        estabelecimento.setRevisadoEm(Instant.now());
        return resposta(estabelecimento);
    }

    /** Tira do guia sem apagar (ex.: fechou ou está temporariamente sem funcionar). */
    @Transactional
    public EstabelecimentoAdminResponse arquivar(Long id) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        estabelecimento.setAtivo(false);
        estabelecimento.setRevisadoEm(Instant.now());
        return resposta(estabelecimento);
    }

    @Transactional
    public EstabelecimentoAdminResponse reativar(Long id) {
        Estabelecimento estabelecimento = buscarEntidade(id);
        estabelecimento.setAtivo(true);
        estabelecimento.setRevisadoEm(Instant.now());
        return resposta(estabelecimento);
    }

    /**
     * Junta um possível duplicado ao cadastro semelhante: os campos vazios do
     * cadastro existente recebem os dados do duplicado, que é recusado (e,
     * assim, não volta nas próximas importações).
     */
    @Transactional
    public EstabelecimentoAdminResponse mesclar(Long id) {
        Estabelecimento duplicado = buscarEntidade(id);
        if (duplicado.getDuplicadoDeId() == null) {
            throw new RegraNegocioException("Este estabelecimento não foi apontado como possível duplicado.");
        }
        Estabelecimento destino = estabelecimentoRepository.findById(duplicado.getDuplicadoDeId())
                .orElseThrow(() -> new RegraNegocioException("O cadastro semelhante não existe mais."));
        if (destino.getSituacao() == SituacaoRevisao.RECUSADO) {
            throw new RegraNegocioException("O cadastro semelhante foi recusado. Aprove ou recuse este registro.");
        }

        boolean mudou = false;
        mudou |= completar(destino::getDescricao, destino::setDescricao, duplicado.getDescricao());
        mudou |= completar(destino::getEndereco, destino::setEndereco, duplicado.getEndereco());
        mudou |= completar(destino::getTelefone, destino::setTelefone, duplicado.getTelefone());
        mudou |= completar(destino::getWhatsapp, destino::setWhatsapp, duplicado.getWhatsapp());
        mudou |= completar(destino::getSite, destino::setSite, duplicado.getSite());
        mudou |= completar(destino::getContato, destino::setContato, duplicado.getContato());
        mudou |= completar(destino::getHorario, destino::setHorario, duplicado.getHorario());
        if (!destino.possuiLocalizacao() && duplicado.possuiLocalizacao()) {
            destino.definirLocalizacao(duplicado.getLatitude(), duplicado.getLongitude());
            destino.setLocalizacaoValidada(duplicado.isLocalizacaoValidada());
            mudou = true;
        }
        if (mudou) {
            destino.marcarAtualizado();
            anotar(destino, "Dados complementados com o registro de " + duplicado.getFonte() + " em "
                    + DATA.format(Instant.now()) + ".");
        }

        duplicado.setSituacao(SituacaoRevisao.RECUSADO);
        duplicado.setRevisadoEm(Instant.now());
        anotar(duplicado, "Mesclado com \"" + destino.getNome() + "\" (#" + destino.getId() + ") em "
                + DATA.format(Instant.now()) + ".");
        return resposta(destino);
    }

    /** Aprova ou recusa vários de uma vez. Devolve quantos foram alterados. */
    @Transactional
    public int executarEmLote(String acao, List<Long> ids) {
        String tipo = TextoUtils.normalizar(acao);
        if (!tipo.equals("aprovar") && !tipo.equals("recusar")) {
            throw new RegraNegocioException("Ação desconhecida: use \"aprovar\" ou \"recusar\".");
        }
        List<Estabelecimento> selecionados = estabelecimentoRepository.findByIdIn(ids.stream().distinct().toList());
        selecionados.forEach(tipo.equals("aprovar") ? this::aprovar : this::recusar);
        return selecionados.size();
    }

    /** Cadastros parecidos com os dados do formulário (verificação antes de salvar). */
    public List<SemelhanteResponse> verificarDuplicados(EstabelecimentoRequest dados, Long ignorarId) {
        Perfil perfil = new Perfil(TextoUtils.limpar(dados.nome()), TextoUtils.limpar(dados.endereco()),
                TextoUtils.limpar(dados.telefone()), TextoUtils.limpar(dados.whatsapp()),
                dados.latitude(), dados.longitude(), Estabelecimento.FONTE_CADASTRO_MANUAL);
        if (perfil.nome() == null) {
            return List.of();
        }
        return new Duplicados(estabelecimentoRepository.findAll()).semelhantes(perfil, ignorarId).stream()
                .filter(semelhanca -> semelhanca.existente().getSituacao() != SituacaoRevisao.RECUSADO)
                .limit(5)
                .map(SemelhanteResponse::de)
                .toList();
    }

    public ResumoAdminResponse resumo() {
        return new ResumoAdminResponse(
                estabelecimentoRepository.countBySituacao(SituacaoRevisao.PENDENTE),
                estabelecimentoRepository.countBySituacaoAndAtivo(SituacaoRevisao.APROVADO, true),
                estabelecimentoRepository.countBySituacaoAndAtivo(SituacaoRevisao.APROVADO, false),
                estabelecimentoRepository.countBySituacao(SituacaoRevisao.RECUSADO),
                estabelecimentoRepository.countBySituacaoAndDuplicadoDeIdIsNotNull(SituacaoRevisao.PENDENTE),
                estabelecimentoRepository.contarPublicosSemLocalizacao(),
                relatoRepository.countByResolvido(false));
    }

    /* ------------------------------------------------------------------ */

    private void aprovar(Estabelecimento estabelecimento) {
        estabelecimento.setSituacao(SituacaoRevisao.APROVADO);
        estabelecimento.setDuplicadoDeId(null); // conferido: não é duplicado
        estabelecimento.setRevisadoEm(Instant.now());
    }

    private void recusar(Estabelecimento estabelecimento) {
        estabelecimento.setSituacao(SituacaoRevisao.RECUSADO);
        estabelecimento.setRevisadoEm(Instant.now());
    }

    private Estabelecimento buscarEntidade(Long id) {
        return estabelecimentoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado."));
    }

    private List<EstabelecimentoAdminResponse> respostas(List<Estabelecimento> lista, boolean comDadosDaFonte) {
        List<Long> idsDuplicados = lista.stream().map(Estabelecimento::getDuplicadoDeId).filter(Objects::nonNull)
                .distinct().toList();
        Map<Long, Estabelecimento> semelhantes = idsDuplicados.isEmpty()
                ? Map.of()
                : estabelecimentoRepository.findByIdIn(idsDuplicados).stream()
                        .collect(Collectors.toMap(Estabelecimento::getId, estabelecimento -> estabelecimento));
        Map<Long, Long> relatos = relatoRepository.contarAbertosPorEstabelecimento().stream()
                .collect(Collectors.toMap(AbertosPorEstabelecimento::getEstabelecimentoId,
                        AbertosPorEstabelecimento::getTotal));
        Map<String, Instant> ultimaImportacao = new HashMap<>();

        return lista.stream().map(estabelecimento -> {
            Long idParecido = estabelecimento.getDuplicadoDeId();
            Estabelecimento parecido = idParecido == null ? null : semelhantes.get(idParecido);
            SemelhanteResponse duplicadoDe = parecido == null ? null
                    : SemelhanteResponse.de(parecido, motivo(estabelecimento, parecido));
            EstabelecimentoAdminResponse resposta = EstabelecimentoAdminResponse.de(estabelecimento, duplicadoDe,
                    ausente(estabelecimento, ultimaImportacao), relatos.getOrDefault(estabelecimento.getId(), 0L));
            return comDadosDaFonte ? resposta : semDadosDaFonte(resposta);
        }).toList();
    }

    /** A lista não leva o JSON original da fonte (fica só no detalhe), para ser leve. */
    private static EstabelecimentoAdminResponse semDadosDaFonte(EstabelecimentoAdminResponse r) {
        return new EstabelecimentoAdminResponse(r.id(), r.nome(), r.categoria(), r.descricao(), r.endereco(),
                r.telefone(), r.whatsapp(), r.site(), r.contato(), r.horario(), r.latitude(), r.longitude(),
                r.imagemUrl(), r.situacao(), r.ativo(), r.localizacaoValidada(), r.fonte(), r.urlFonte(), r.fonteId(),
                r.tipoFonte(), null, r.duplicadoDe(), r.observacoes(), r.criadoEm(), r.atualizadoEm(),
                r.vistoNaFonteEm(), r.revisadoEm(), r.ausenteNaUltimaImportacao(), r.relatosAbertos());
    }

    private static String motivo(Estabelecimento estabelecimento, Estabelecimento parecido) {
        return Duplicados.comparar(Perfil.de(estabelecimento), Perfil.de(parecido), parecido).motivo();
    }

    /** A última importação bem-sucedida da fonte não trouxe mais este registro. */
    private boolean ausente(Estabelecimento estabelecimento, Map<String, Instant> cache) {
        String chave = ImportacaoService.chaveDaFonte(estabelecimento.getFonteId());
        if (chave == null || estabelecimento.getVistoNaFonteEm() == null) {
            return false;
        }
        Instant ultima = cache.computeIfAbsent(chave, fonte -> importacaoRepository
                .findFirstByFonteAndSituacaoOrderByIniciadaEmDesc(fonte, Importacao.Situacao.CONCLUIDA)
                .map(Importacao::getIniciadaEm).orElse(Instant.EPOCH));
        return estabelecimento.getVistoNaFonteEm().isBefore(ultima);
    }

    private static boolean atendeSituacao(Estabelecimento estabelecimento, String filtro) {
        return switch (filtro) {
            case PUBLICADO -> estabelecimento.isPublico();
            case ARQUIVADO -> estabelecimento.getSituacao() == SituacaoRevisao.APROVADO && !estabelecimento.isAtivo();
            default -> estabelecimento.getSituacao().name().equals(filtro);
        };
    }

    private static SituacaoRevisao situacao(String filtro) {
        try {
            return SituacaoRevisao.valueOf(filtro);
        } catch (IllegalArgumentException erro) {
            throw new RegraNegocioException("Situação desconhecida: " + filtro + ".");
        }
    }

    private static boolean daFonte(Estabelecimento estabelecimento, String fonte) {
        if (fonte.equals("manual")) {
            return estabelecimento.getFonteId() == null;
        }
        return fonte.equals(ImportacaoService.chaveDaFonte(estabelecimento.getFonteId()));
    }

    private static boolean corresponde(Estabelecimento estabelecimento, Set<String> termos) {
        String texto = TextoUtils.normalizar(String.join(" | ", estabelecimento.getNome(),
                Objects.toString(estabelecimento.getEndereco(), ""),
                Objects.toString(estabelecimento.getTelefone(), ""),
                Objects.toString(estabelecimento.getDescricao(), ""),
                estabelecimento.getCategoria().getNome()));
        return Busca.corresponde(texto, termos);
    }

    private static boolean completar(Supplier<String> atual, Consumer<String> definir, String novo) {
        if (novo == null || novo.isBlank() || (atual.get() != null && !atual.get().isBlank())) {
            return false;
        }
        definir.accept(novo);
        return true;
    }

    private static void anotar(Estabelecimento estabelecimento, String texto) {
        String atual = estabelecimento.getObservacaoRevisao();
        String novo = atual == null || atual.isBlank() ? texto : atual + "\n" + texto;
        estabelecimento.setObservacaoRevisao(novo.length() > 1000 ? novo.substring(novo.length() - 1000) : novo);
    }
}
