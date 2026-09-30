package br.com.rumocap.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import br.com.rumocap.service.ImagemService;

/**
 * Publica as imagens enviadas pela área administrativa no endereço /uploads/{arquivo}.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final ImagemService imagemService;

    public WebConfig(ImagemService imagemService) {
        this.imagemService = imagemService;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String pasta = imagemService.getDiretorio().toUri().toString();
        if (!pasta.endsWith("/")) {
            pasta += "/";
        }
        registry.addResourceHandler("/uploads/**").addResourceLocations(pasta);
    }
}
