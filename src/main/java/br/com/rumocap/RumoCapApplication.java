package br.com.rumocap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * RumoCap - Guia Comercial de Capitão Poço (PA).
 * <p>
 * Portal informativo sobre estabelecimentos e serviços do município.
 * A mesma aplicação publica a API REST (/api) e as páginas do site.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class RumoCapApplication {

    public static void main(String[] args) {
        SpringApplication.run(RumoCapApplication.class, args);
    }
}
