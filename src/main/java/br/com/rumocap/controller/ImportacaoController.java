package br.com.rumocap.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.rumocap.dto.FonteResponse;
import br.com.rumocap.dto.ImportacaoResponse;
import br.com.rumocap.levantamento.ImportacaoService;

/** Levantamento de estabelecimentos em fontes públicas (somente administrador). */
@RestController
@RequestMapping("/api/admin/importacoes")
public class ImportacaoController {

    private final ImportacaoService importacaoService;

    public ImportacaoController(ImportacaoService importacaoService) {
        this.importacaoService = importacaoService;
    }

    @GetMapping("/fontes")
    public List<FonteResponse> fontes() {
        return importacaoService.listarFontes();
    }

    @GetMapping
    public List<ImportacaoResponse> historico() {
        return importacaoService.historico();
    }

    /**
     * Consulta a fonte ("osm", "cnes" ou "cnefe") e grava os locais encontrados
     * como "aguardando revisão". Se a fonte estiver fora do ar, a resposta traz
     * a situação FALHOU e nada é alterado.
     */
    @PostMapping("/{fonte}")
    public ImportacaoResponse importar(@PathVariable("fonte") String fonte) {
        return importacaoService.importar(fonte);
    }
}
