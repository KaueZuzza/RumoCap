package br.com.rumocap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações próprias do RumoCap (prefixo "rumocap" no application.properties).
 *
 * @param admin  usuário e senha da área administrativa
 * @param upload pasta onde as imagens dos estabelecimentos são gravadas
 */
@ConfigurationProperties(prefix = "rumocap")
public record RumoCapProperties(Admin admin, Upload upload) {

    public record Admin(String usuario, String senha) {
    }

    public record Upload(String diretorio) {
    }
}
