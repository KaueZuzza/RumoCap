package br.com.rumocap.controller;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.rumocap.dto.AcaoEmLoteRequest;
import br.com.rumocap.dto.EstabelecimentoAdminResponse;
import br.com.rumocap.dto.EstabelecimentoRequest;
import br.com.rumocap.dto.SemelhanteResponse;
import br.com.rumocap.service.RevisaoService;

/**
 * Revisão de estabelecimentos (somente administrador): todos os cadastros,
 * em qualquer situação, e as ações de aprovar, recusar, arquivar e mesclar.
 */
@RestController
@RequestMapping("/api/admin/estabelecimentos")
public class RevisaoController {

    private final RevisaoService revisaoService;

    public RevisaoController(RevisaoService revisaoService) {
        this.revisaoService = revisaoService;
    }

    /** Ex.: GET /api/admin/estabelecimentos?situacao=PENDENTE&fonte=cnes&busca=clinica */
    @GetMapping
    public List<EstabelecimentoAdminResponse> listar(
            @RequestParam(name = "situacao", required = false) String situacao,
            @RequestParam(name = "busca", required = false) String busca,
            @RequestParam(name = "categoriaId", required = false) Long categoriaId,
            @RequestParam(name = "fonte", required = false) String fonte) {
        return revisaoService.listar(situacao, busca, categoriaId, fonte);
    }

    @GetMapping("/{id}")
    public EstabelecimentoAdminResponse buscar(@PathVariable("id") Long id) {
        return revisaoService.buscar(id);
    }

    @PostMapping("/{id}/aprovar")
    public EstabelecimentoAdminResponse aprovar(@PathVariable("id") Long id) {
        return revisaoService.aprovar(id);
    }

    @PostMapping("/{id}/recusar")
    public EstabelecimentoAdminResponse recusar(@PathVariable("id") Long id) {
        return revisaoService.recusar(id);
    }

    @PostMapping("/{id}/revisar")
    public EstabelecimentoAdminResponse voltarParaRevisao(@PathVariable("id") Long id) {
        return revisaoService.voltarParaRevisao(id);
    }

    @PostMapping("/{id}/arquivar")
    public EstabelecimentoAdminResponse arquivar(@PathVariable("id") Long id) {
        return revisaoService.arquivar(id);
    }

    @PostMapping("/{id}/reativar")
    public EstabelecimentoAdminResponse reativar(@PathVariable("id") Long id) {
        return revisaoService.reativar(id);
    }

    /** Junta o possível duplicado ao cadastro semelhante. Devolve o cadastro que permaneceu. */
    @PostMapping("/{id}/mesclar")
    public EstabelecimentoAdminResponse mesclar(@PathVariable("id") Long id) {
        return revisaoService.mesclar(id);
    }

    /** Body: {"acao": "aprovar", "ids": [1, 2, 3]} */
    @PostMapping("/lote")
    public Map<String, Integer> executarEmLote(@Valid @RequestBody AcaoEmLoteRequest dados) {
        return Map.of("alterados", revisaoService.executarEmLote(dados.acao(), dados.ids()));
    }

    /** Antes de salvar um cadastro manual: há estabelecimentos parecidos? */
    @PostMapping("/verificar-duplicados")
    public List<SemelhanteResponse> verificarDuplicados(
            @RequestBody EstabelecimentoRequest dados,
            @RequestParam(name = "ignorarId", required = false) Long ignorarId) {
        return revisaoService.verificarDuplicados(dados, ignorarId);
    }
}
