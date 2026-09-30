package br.com.rumocap.controller;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import br.com.rumocap.dto.EstabelecimentoRequest;
import br.com.rumocap.dto.EstabelecimentoResponse;
import br.com.rumocap.dto.MarcadorResponse;
import br.com.rumocap.service.EstabelecimentoService;

@RestController
@RequestMapping("/api/estabelecimentos")
public class EstabelecimentoController {

    private final EstabelecimentoService estabelecimentoService;

    public EstabelecimentoController(EstabelecimentoService estabelecimentoService) {
        this.estabelecimentoService = estabelecimentoService;
    }

    /** Ex.: GET /api/estabelecimentos?busca=farmacia&categoriaId=2 */
    @GetMapping
    public List<EstabelecimentoResponse> listar(
            @RequestParam(name = "busca", required = false) String busca,
            @RequestParam(name = "categoriaId", required = false) Long categoriaId) {
        return estabelecimentoService.listar(busca, categoriaId);
    }

    /** Marcadores do mapa: somente estabelecimentos com latitude e longitude. */
    @GetMapping("/mapa")
    public List<MarcadorResponse> listarMarcadores(
            @RequestParam(name = "categoriaId", required = false) Long categoriaId) {
        return estabelecimentoService.listarMarcadores(categoriaId);
    }

    @GetMapping("/{id}")
    public EstabelecimentoResponse buscar(@PathVariable("id") Long id) {
        return estabelecimentoService.buscar(id);
    }

    @PostMapping
    public ResponseEntity<EstabelecimentoResponse> criar(@Valid @RequestBody EstabelecimentoRequest dados) {
        EstabelecimentoResponse criado = estabelecimentoService.criar(dados);
        return ResponseEntity.created(URI.create("/api/estabelecimentos/" + criado.id())).body(criado);
    }

    @PutMapping("/{id}")
    public EstabelecimentoResponse atualizar(@PathVariable("id") Long id,
                                             @Valid @RequestBody EstabelecimentoRequest dados) {
        return estabelecimentoService.atualizar(id, dados);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable("id") Long id) {
        estabelecimentoService.excluir(id);
    }

    /** Envia a imagem/logo (multipart, campo "arquivo"). Substitui a imagem anterior, se houver. */
    @PostMapping(path = "/{id}/imagem", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public EstabelecimentoResponse enviarImagem(@PathVariable("id") Long id,
                                                @RequestPart("arquivo") MultipartFile arquivo) {
        return estabelecimentoService.atualizarImagem(id, arquivo);
    }

    @DeleteMapping("/{id}/imagem")
    public EstabelecimentoResponse removerImagem(@PathVariable("id") Long id) {
        return estabelecimentoService.removerImagem(id);
    }
}
