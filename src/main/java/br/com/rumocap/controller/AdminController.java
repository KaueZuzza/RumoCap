package br.com.rumocap.controller;

import java.util.Map;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Usado pela tela de login da área administrativa para conferir usuário e senha. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/sessao")
    public Map<String, String> sessao(Authentication autenticacao) {
        return Map.of("usuario", autenticacao.getName());
    }
}
