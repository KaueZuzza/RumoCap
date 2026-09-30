package br.com.rumocap.service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import br.com.rumocap.dto.RelatoRequest;
import br.com.rumocap.dto.RelatoResponse;
import br.com.rumocap.exception.LimiteExcedidoException;
import br.com.rumocap.exception.RecursoNaoEncontradoException;
import br.com.rumocap.model.Estabelecimento;
import br.com.rumocap.model.Relato;
import br.com.rumocap.repository.EstabelecimentoRepository;
import br.com.rumocap.repository.RelatoRepository;
import br.com.rumocap.util.TextoUtils;

/**
 * "Informação incorreta?": avisos dos visitantes sobre dados errados de um
 * estabelecimento. Ficam guardados para a administração conferir e corrigir.
 */
@Service
@Transactional(readOnly = true)
public class RelatoService {

    /** Limite por endereço IP, para evitar abusos: 5 avisos a cada 10 minutos. */
    private static final int LIMITE_POR_ORIGEM = 5;
    private static final Duration JANELA = Duration.ofMinutes(10);

    private final RelatoRepository relatoRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final Map<String, Deque<Instant>> envios = new ConcurrentHashMap<>();

    public RelatoService(RelatoRepository relatoRepository, EstabelecimentoRepository estabelecimentoRepository) {
        this.relatoRepository = relatoRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
    }

    /**
     * Registra o aviso. Envios de robôs (campo invisível preenchido) são
     * aceitos sem gravar nada, para não ensinar o robô a contornar a armadilha.
     */
    @Transactional
    public void registrar(Long estabelecimentoId, RelatoRequest dados, String origem) {
        Estabelecimento estabelecimento = estabelecimentoRepository.findById(estabelecimentoId)
                .filter(Estabelecimento::isPublico)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Estabelecimento não encontrado."));
        if (dados.site() != null && !dados.site().isBlank()) {
            return;
        }
        verificarLimite(origem);
        relatoRepository.save(new Relato(estabelecimento.getId(), dados.mensagem().strip(),
                TextoUtils.limpar(dados.contato())));
    }

    public List<RelatoResponse> listar(boolean resolvidos) {
        List<Relato> relatos = relatoRepository.findByResolvidoOrderByCriadoEmDesc(resolvidos);
        Map<Long, String> nomes = estabelecimentoRepository.findByIdIn(
                        relatos.stream().map(Relato::getEstabelecimentoId).distinct().toList()).stream()
                .collect(Collectors.toMap(Estabelecimento::getId, Estabelecimento::getNome));
        return relatos.stream()
                .map(relato -> RelatoResponse.de(relato, nomes.getOrDefault(relato.getEstabelecimentoId(), "")))
                .toList();
    }

    @Transactional
    public void resolver(Long id) {
        buscar(id).marcarResolvido();
    }

    @Transactional
    public void excluir(Long id) {
        relatoRepository.delete(buscar(id));
    }

    private Relato buscar(Long id) {
        return relatoRepository.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Aviso não encontrado."));
    }

    private void verificarLimite(String origem) {
        Instant agora = Instant.now();
        Deque<Instant> recentes = envios.computeIfAbsent(origem == null ? "desconhecida" : origem,
                chave -> new ArrayDeque<>());
        synchronized (recentes) {
            while (!recentes.isEmpty() && recentes.peekFirst().isBefore(agora.minus(JANELA))) {
                recentes.pollFirst();
            }
            if (recentes.size() >= LIMITE_POR_ORIGEM) {
                throw new LimiteExcedidoException(
                        "Muitos avisos enviados em pouco tempo. Tente novamente em alguns minutos.");
            }
            recentes.addLast(agora);
        }
        if (envios.size() > 10_000) {
            envios.clear(); // evita crescer sem limite em um servidor que fica muito tempo no ar
        }
    }
}
