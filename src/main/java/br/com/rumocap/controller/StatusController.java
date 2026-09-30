package br.com.rumocap.controller;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import br.com.rumocap.dto.StatusResponse;
import br.com.rumocap.repository.CategoriaRepository;
import br.com.rumocap.repository.EstabelecimentoRepository;

/** GET /api/status — confirma que a API responde e que o banco está acessível. */
@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final CategoriaRepository categoriaRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;

    public StatusController(CategoriaRepository categoriaRepository,
                            EstabelecimentoRepository estabelecimentoRepository) {
        this.categoriaRepository = categoriaRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
    }

    @GetMapping
    public ResponseEntity<StatusResponse> status() {
        try {
            return ResponseEntity.ok(new StatusResponse("online", "conectado",
                    categoriaRepository.count(), estabelecimentoRepository.count()));
        } catch (DataAccessException erro) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new StatusResponse("online", "indisponível", null, null));
        }
    }
}
