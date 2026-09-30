package br.com.rumocap.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import br.com.rumocap.dto.RelatoResponse;
import br.com.rumocap.dto.ResumoAdminResponse;
import br.com.rumocap.service.RelatoService;
import br.com.rumocap.service.RevisaoService;

/** Sessão, resumo do painel e avisos de "informação incorreta" (somente administrador). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RevisaoService revisaoService;
    private final RelatoService relatoService;

    public AdminController(RevisaoService revisaoService, RelatoService relatoService) {
        this.revisaoService = revisaoService;
        this.relatoService = relatoService;
    }

    /** Usado pela tela de login para conferir usuário e senha. */
    @GetMapping("/sessao")
    public Map<String, String> sessao(Authentication autenticacao) {
        return Map.of("usuario", autenticacao.getName());
    }

    @GetMapping("/resumo")
    public ResumoAdminResponse resumo() {
        return revisaoService.resumo();
    }

    @GetMapping("/relatos")
    public List<RelatoResponse> relatos(@RequestParam(name = "resolvidos", defaultValue = "false") boolean resolvidos) {
        return relatoService.listar(resolvidos);
    }

    @PostMapping("/relatos/{id}/resolver")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolverRelato(@PathVariable("id") Long id) {
        relatoService.resolver(id);
    }

    @DeleteMapping("/relatos/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluirRelato(@PathVariable("id") Long id) {
        relatoService.excluir(id);
    }
}
