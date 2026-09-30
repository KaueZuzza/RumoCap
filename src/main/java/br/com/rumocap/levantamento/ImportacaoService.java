package br.com.rumocap.levantamento;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import br.com.rumocap.dto.FonteResponse;
import br.com.rumocap.dto.ImportacaoResponse;
import br.com.rumocap.exception.ConflitoException;
import br.com.rumocap.exception.RecursoNaoEncontradoException;
import br.com.rumocap.levantamento.Duplicados.Perfil;
import br.com.rumocap.levantamento.Duplicados.Semelhanca;
import br.com.rumocap.model.Categoria;
import br.com.rumocap.model.Estabelecimento;
import br.com.rumocap.model.Importacao;
import br.com.rumocap.model.SituacaoRevisao;
import br.com.rumocap.repository.CategoriaRepository;
import br.com.rumocap.repository.EstabelecimentoRepository;
import br.com.rumocap.repository.ImportacaoRepository;
import br.com.rumocap.util.TextoUtils;
import tools.jackson.databind.json.JsonMapper;

/**
 * Levantamento de estabelecimentos: Fonte pública -> validação e categorização
 * -> verificação de duplicados -> PostgreSQL (aguardando revisão).
 * <p>
 * Regras:
 * <ul>
 *     <li>todo local novo entra como PENDENTE e só aparece no guia depois de aprovado;</li>
 *     <li>um registro já importado (mesmo identificador na fonte) nunca é duplicado: os
 *         campos vazios são completados e as diferenças viram observações para a revisão,
 *         sem sobrescrever o que a administração editou;</li>
 *     <li>locais recusados não voltam para a revisão;</li>
 *     <li>nada é apagado porque a fonte deixou de trazer um local.</li>
 * </ul>
 */
@Service
public class ImportacaoService {

    private static final Logger log = LoggerFactory.getLogger(ImportacaoService.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int LIMITE_DADOS_FONTE = 4000;
    private static final int LIMITE_OBSERVACAO = 1000;

    private final Map<String, FonteDeDados> fontes = new LinkedHashMap<>();
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final CategoriaRepository categoriaRepository;
    private final ImportacaoRepository importacaoRepository;
    private final TransactionTemplate transacao;
    private final Set<String> emAndamento = ConcurrentHashMap.newKeySet();

    public ImportacaoService(List<FonteDeDados> fontesDisponiveis,
                             EstabelecimentoRepository estabelecimentoRepository,
                             CategoriaRepository categoriaRepository,
                             ImportacaoRepository importacaoRepository,
                             PlatformTransactionManager gerenciadorDeTransacoes) {
        fontesDisponiveis.forEach(fonte -> fontes.put(fonte.chave(), fonte));
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.categoriaRepository = categoriaRepository;
        this.importacaoRepository = importacaoRepository;
        this.transacao = new TransactionTemplate(gerenciadorDeTransacoes);
    }

    /** Fontes disponíveis, com a última importação de cada uma. */
    public List<FonteResponse> listarFontes() {
        Map<String, Long> porFonte = new LinkedHashMap<>();
        for (Estabelecimento estabelecimento : estabelecimentoRepository.findAll()) {
            String chave = chaveDaFonte(estabelecimento.getFonteId());
            if (chave != null) {
                porFonte.merge(chave, 1L, Long::sum);
            }
        }
        return fontes.values().stream()
                .map(fonte -> new FonteResponse(fonte.chave(), fonte.nome(), fonte.descricao(), fonte.licenca(),
                        fonte.endereco(),
                        importacaoRepository.findFirstByFonteOrderByIniciadaEmDesc(fonte.chave())
                                .map(ImportacaoResponse::de).orElse(null),
                        porFonte.getOrDefault(fonte.chave(), 0L)))
                .toList();
    }

    public List<ImportacaoResponse> historico() {
        return importacaoRepository.findTop30ByOrderByIniciadaEmDesc().stream().map(ImportacaoResponse::de).toList();
    }

    /** "osm:node/123" -> "osm". */
    public static String chaveDaFonte(String fonteId) {
        if (fonteId == null) {
            return null;
        }
        int separador = fonteId.indexOf(':');
        return separador > 0 ? fonteId.substring(0, separador) : null;
    }

    /**
     * Consulta a fonte e grava o resultado. Se a fonte estiver fora do ar, a
     * importação é registrada como FALHOU e nada é alterado no banco.
     */
    public ImportacaoResponse importar(String chave) {
        FonteDeDados fonte = fontes.get(chave);
        if (fonte == null) {
            throw new RecursoNaoEncontradoException("Fonte de dados desconhecida: " + chave + ".");
        }
        if (!emAndamento.add(chave)) {
            throw new ConflitoException("Já existe uma importação de " + fonte.nome() + " em andamento. Aguarde.");
        }
        try {
            Long id = transacao.execute(status -> importacaoRepository.save(new Importacao(chave)).getId());
            Coleta coleta;
            try {
                coleta = fonte.coletar();
            } catch (FonteIndisponivelException erro) {
                log.warn("Importação de {} não concluída: {}", chave, erro.getMessage());
                return registrarFalha(id, erro.getMessage());
            } catch (RuntimeException erro) {
                log.error("Erro inesperado ao ler a fonte {}", chave, erro);
                return registrarFalha(id, "Erro inesperado ao ler os dados da fonte: " + erro.getMessage());
            }
            return transacao.execute(status -> gravar(fonte, coleta, id));
        } finally {
            emAndamento.remove(chave);
        }
    }

    private ImportacaoResponse registrarFalha(Long id, String motivo) {
        return transacao.execute(status -> {
            Importacao importacao = importacaoRepository.findById(id).orElseThrow();
            importacao.falhar(motivo + " Nenhum dado foi alterado; tente novamente mais tarde.");
            return ImportacaoResponse.de(importacao);
        });
    }

    private ImportacaoResponse gravar(FonteDeDados fonte, Coleta coleta, Long idImportacao) {
        Importacao importacao = importacaoRepository.findById(idImportacao).orElseThrow();
        Map<String, Categoria> categorias = new LinkedHashMap<>();
        categoriaRepository.findAll().forEach(categoria -> categorias.put(TextoUtils.normalizar(categoria.getNome()), categoria));
        Duplicados indice = new Duplicados(estabelecimentoRepository.findAll());
        Instant agora = Instant.now();

        for (Candidato candidato : coleta.getCandidatos()) {
            Optional<Estabelecimento> existente = indice.porFonteId(candidato.getFonteId());
            if (existente.isPresent()) {
                atualizarExistente(existente.get(), candidato, agora, importacao);
                continue;
            }

            Perfil perfil = Perfil.de(candidato, fonte.nome());
            Optional<Semelhanca> semelhante = indice.maisSemelhante(perfil, null);
            if (semelhante.isPresent() && semelhante.get().repeticaoNaMesmaFonte()) {
                importacao.setRepetidos(importacao.getRepetidos() + 1);
                continue;
            }

            Estabelecimento novo = novoEstabelecimento(fonte, candidato, categoria(categorias, candidato), agora);
            if (semelhante.isPresent()) {
                Estabelecimento parecido = semelhante.get().existente();
                novo.setDuplicadoDeId(parecido.getId());
                adicionarObservacao(novo, "Possível duplicado de \"" + parecido.getNome() + "\" (" + parecido.getFonte()
                        + "): " + semelhante.get().motivo() + ".");
                importacao.setPossiveisDuplicados(importacao.getPossiveisDuplicados() + 1);
            }
            if (!novo.possuiLocalizacao()) {
                importacao.setLocalizacaoPendente(importacao.getLocalizacaoPendente() + 1);
            }
            estabelecimentoRepository.save(novo);
            indice.adicionar(novo);
            importacao.setNovos(importacao.getNovos() + 1);
        }

        importacao.setEncontrados(coleta.getCandidatos().size());
        importacao.setIgnorados(coleta.totalDescartado());
        List<String> detalhes = new ArrayList<>();
        coleta.getDescartes().forEach((motivo, quantidade) -> detalhes.add(motivo + ": " + quantidade));
        importacao.setDetalhes(limitar(String.join("\n", detalhes), 2000));
        importacao.concluir(resumo(importacao));
        log.info("Importação de {} concluída: {}", fonte.chave(), importacao.getMensagem());
        return ImportacaoResponse.de(importacao);
    }

    private Estabelecimento novoEstabelecimento(FonteDeDados fonte, Candidato candidato, Categoria categoria,
                                                Instant agora) {
        Estabelecimento novo = new Estabelecimento();
        novo.setNome(limitar(candidato.getNome(), 120));
        novo.setCategoria(categoria);
        novo.setDescricao(limitar(candidato.getDescricao(), 400));
        novo.setEndereco(limitar(candidato.getEndereco(), 255));
        novo.setTelefone(candidato.getTelefone());
        novo.setWhatsapp(candidato.getWhatsapp());
        novo.setSite(limitar(candidato.getSite(), 255));
        novo.setContato(limitar(candidato.getContato(), 500));
        novo.setHorario(limitar(candidato.getHorario(), 500));
        novo.definirLocalizacao(candidato.getLatitude(), candidato.getLongitude());
        novo.setFonte(fonte.nome());
        novo.setUrlFonte(limitar(candidato.getUrlFonte(), 500));
        novo.setFonteId(candidato.getFonteId());
        novo.setTipoFonte(limitar(candidato.getTipoFonte(), 200));
        novo.setDadosFonte(json(candidato.getDadosFonte()));
        novo.setSituacao(SituacaoRevisao.PENDENTE);
        novo.setAtivo(true);
        novo.setLocalizacaoValidada(false);
        novo.setVistoNaFonteEm(agora);
        candidato.getObservacoes().forEach(observacao -> adicionarObservacao(novo, observacao));
        if (!TextoUtils.normalizar(categoria.getNome()).equals(TextoUtils.normalizar(candidato.getCategoria()))) {
            adicionarObservacao(novo, "Categoria sugerida pela fonte: " + candidato.getCategoria() + " (não existe no guia).");
        }
        return novo;
    }

    /**
     * Registro já importado antes: completa só os campos vazios e anota as
     * diferenças, sem mudar o que a administração conferiu ou editou.
     */
    private void atualizarExistente(Estabelecimento existente, Candidato candidato, Instant agora, Importacao importacao) {
        existente.setVistoNaFonteEm(agora);
        String dados = json(candidato.getDadosFonte());
        if (!Objects.equals(dados, existente.getDadosFonte())) {
            existente.setDadosFonte(dados);
        }
        if (existente.getSituacao() == SituacaoRevisao.RECUSADO) {
            importacao.setJaRecusados(importacao.getJaRecusados() + 1);
            return;
        }
        boolean mudou = false;
        mudou |= completar(existente::getDescricao, existente::setDescricao, limitar(candidato.getDescricao(), 400));
        mudou |= completar(existente::getEndereco, existente::setEndereco, limitar(candidato.getEndereco(), 255));
        mudou |= completar(existente::getTelefone, existente::setTelefone, candidato.getTelefone());
        mudou |= completar(existente::getWhatsapp, existente::setWhatsapp, candidato.getWhatsapp());
        mudou |= completar(existente::getSite, existente::setSite, limitar(candidato.getSite(), 255));
        mudou |= completar(existente::getContato, existente::setContato, limitar(candidato.getContato(), 500));
        mudou |= completar(existente::getHorario, existente::setHorario, limitar(candidato.getHorario(), 500));
        if (existente.getUrlFonte() == null && candidato.getUrlFonte() != null) {
            existente.setUrlFonte(limitar(candidato.getUrlFonte(), 500));
        }
        if (!existente.possuiLocalizacao() && candidato.possuiLocalizacao() && !existente.isLocalizacaoValidada()) {
            existente.definirLocalizacao(candidato.getLatitude(), candidato.getLongitude());
            mudou = true;
        }

        if (mudou) {
            existente.marcarAtualizado();
        }
        mudou |= anotarDiferencas(existente, candidato);
        if (mudou) {
            importacao.setAtualizados(importacao.getAtualizados() + 1);
        } else {
            importacao.setSemAlteracao(importacao.getSemAlteracao() + 1);
        }
    }

    private boolean anotarDiferencas(Estabelecimento existente, Candidato candidato) {
        String antes = existente.getObservacaoRevisao();
        if (candidato.getNome() != null && Similaridade.nomes(candidato.getNome(), existente.getNome()) < 0.9) {
            adicionarObservacao(existente, "Na fonte, o nome agora é \"" + candidato.getNome() + "\".");
        }
        if (candidato.getTelefone() != null && existente.getTelefone() != null
                && !Objects.equals(Telefones.chave(candidato.getTelefone()), Telefones.chave(existente.getTelefone()))) {
            adicionarObservacao(existente, "A fonte agora informa o telefone " + candidato.getTelefone() + ".");
        }
        if (candidato.getEndereco() != null && existente.getEndereco() != null
                && !candidato.getEndereco().equals(existente.getEndereco())
                && Similaridade.enderecos(candidato.getEndereco(), existente.getEndereco()) < 0.9) {
            adicionarObservacao(existente, "A fonte agora informa o endereço " + candidato.getEndereco() + ".");
        }
        if (candidato.possuiLocalizacao() && existente.possuiLocalizacao()) {
            double distancia = Municipio.distanciaEmMetros(candidato.getLatitude(), candidato.getLongitude(),
                    existente.getLatitude(), existente.getLongitude());
            if (distancia > 50) {
                adicionarObservacao(existente, "A fonte agora posiciona o local a " + Math.round(distancia)
                        + " m do marcador atual.");
            }
        }
        return !Objects.equals(antes, existente.getObservacaoRevisao());
    }

    private static boolean completar(Supplier<String> atual, Consumer<String> definir, String novo) {
        if (novo == null || (atual.get() != null && !atual.get().isBlank())) {
            return false;
        }
        definir.accept(novo);
        return true;
    }

    private static Categoria categoria(Map<String, Categoria> categorias, Candidato candidato) {
        Categoria categoria = categorias.get(TextoUtils.normalizar(candidato.getCategoria()));
        if (categoria == null) {
            categoria = categorias.get(TextoUtils.normalizar(Categorias.OUTROS));
        }
        if (categoria == null) {
            categoria = categorias.values().iterator().next();
        }
        return categoria;
    }

    /** Acrescenta uma linha às observações da revisão, sem repetir e sem passar do limite da coluna. */
    static void adicionarObservacao(Estabelecimento estabelecimento, String observacao) {
        String atual = estabelecimento.getObservacaoRevisao();
        if (atual != null && atual.lines().anyMatch(linha -> linha.equals(observacao))) {
            return;
        }
        String novo = atual == null || atual.isBlank() ? observacao : atual + "\n" + observacao;
        estabelecimento.setObservacaoRevisao(limitar(novo, LIMITE_OBSERVACAO));
    }

    private static String resumo(Importacao importacao) {
        return importacao.getEncontrados() + " encontrado(s): " + importacao.getNovos() + " novo(s) aguardando revisão, "
                + importacao.getAtualizados() + " atualizado(s), " + importacao.getSemAlteracao() + " sem alteração, "
                + importacao.getPossiveisDuplicados() + " possível(is) duplicado(s), "
                + importacao.getLocalizacaoPendente() + " com localização pendente. "
                + importacao.getIgnorados() + " registro(s) da fonte descartado(s) pelos filtros.";
    }

    /** JSON dos dados originais, removendo campos do fim se passar do tamanho da coluna. */
    static String json(Map<String, Object> dados) {
        if (dados.isEmpty()) {
            return null;
        }
        Map<String, Object> copia = new LinkedHashMap<>(dados);
        String texto = JSON.writeValueAsString(copia);
        while (texto.length() > LIMITE_DADOS_FONTE && !copia.isEmpty()) {
            copia.remove(new ArrayList<>(copia.keySet()).get(copia.size() - 1));
            texto = JSON.writeValueAsString(copia);
        }
        return copia.isEmpty() ? null : texto;
    }

    static String limitar(String texto, int maximo) {
        if (texto == null || texto.length() <= maximo) {
            return texto;
        }
        return texto.substring(0, maximo - 1).strip() + "…";
    }
}
