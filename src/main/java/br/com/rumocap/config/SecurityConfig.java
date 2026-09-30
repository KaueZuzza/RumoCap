package br.com.rumocap.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Regras de acesso:
 * <ul>
 *     <li>páginas do site, imagens e consultas (GET /api/**) são públicas;</li>
 *     <li>o aviso de "informação incorreta" (POST /api/estabelecimentos/{id}/relatos) é público;</li>
 *     <li>cadastrar, editar e excluir (POST, PUT e DELETE em /api/**) exigem o login do administrador;</li>
 *     <li>tudo em /api/admin/** (revisão, importações, relatos) exige o login, inclusive as consultas.</li>
 * </ul>
 * O login usa HTTP Basic: a página admin.html envia o usuário e a senha em cada requisição.
 */
@Configuration
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(acesso -> acesso
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/estabelecimentos/*/relatos").permitAll()
                        .requestMatchers("/api/**").hasRole("ADMIN")
                        .anyRequest().permitAll())
                // Respostas em JSON e sem o cabeçalho WWW-Authenticate,
                // para o navegador não abrir a janela nativa de login.
                .httpBasic(basic -> basic.authenticationEntryPoint((requisicao, resposta, erro) ->
                        responderErro(resposta, HttpServletResponse.SC_UNAUTHORIZED,
                                "Usuário ou senha inválidos.")))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint((requisicao, resposta, erro) ->
                                responderErro(resposta, HttpServletResponse.SC_UNAUTHORIZED,
                                        "Acesso restrito. Faça login na área administrativa."))
                        .accessDeniedHandler((requisicao, resposta, erro) ->
                                responderErro(resposta, HttpServletResponse.SC_FORBIDDEN,
                                        "Você não tem permissão para realizar esta operação.")));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService administradores(RumoCapProperties propriedades, PasswordEncoder passwordEncoder) {
        RumoCapProperties.Admin admin = propriedades.admin();
        if (admin.senha() == null || admin.senha().isBlank()) {
            throw new IllegalStateException("A senha da área administrativa não foi definida. "
                    + "Defina RUMOCAP_ADMIN_PASSWORD ou crie config/application.properties "
                    + "(veja config/application.properties.example).");
        }
        if (admin.senha().length() < 8) {
            log.warn("A senha da área administrativa tem menos de 8 caracteres. "
                    + "Use uma senha mais forte antes de publicar o site.");
        }
        return new InMemoryUserDetailsManager(User.withUsername(admin.usuario())
                .password(passwordEncoder.encode(admin.senha()))
                .roles("ADMIN")
                .build());
    }

    private static void responderErro(HttpServletResponse resposta, int status, String mensagem)
            throws IOException {
        resposta.setStatus(status);
        resposta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resposta.setContentType(MediaType.APPLICATION_JSON_VALUE);
        resposta.getWriter().write("{\"status\":" + status + ",\"mensagem\":\"" + mensagem + "\"}");
    }
}
