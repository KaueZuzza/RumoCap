package br.com.rumocap.levantamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Categorização automática")
class CategoriasTest {

    @Test
    @DisplayName("usa o tipo do OpenStreetMap")
    void peloOpenStreetMap() {
        assertThat(Categorias.pelasTagsOsm(Map.of("amenity", "pharmacy"))).containsExactly("amenity=pharmacy", "Saúde");
        assertThat(Categorias.pelasTagsOsm(Map.of("amenity", "restaurant"))[1]).isEqualTo("Alimentação");
        assertThat(Categorias.pelasTagsOsm(Map.of("shop", "supermarket"))[1]).isEqualTo("Mercados");
        assertThat(Categorias.pelasTagsOsm(Map.of("shop", "hairdresser"))[1]).isEqualTo("Moda e Beleza");
        assertThat(Categorias.pelasTagsOsm(Map.of("amenity", "internet_cafe"))[1]).isEqualTo("Tecnologia");
        assertThat(Categorias.pelasTagsOsm(Map.of("tourism", "hotel"))[1]).isEqualTo("Serviços");
        assertThat(Categorias.pelasTagsOsm(Map.of("shop", "hardware"))[1]).isEqualTo("Casa e Construção");
        assertThat(Categorias.pelasTagsOsm(Map.of("shop", "car_repair"))[1]).isEqualTo("Automotivo");
        assertThat(Categorias.pelasTagsOsm(Map.of("healthcare", "laboratory"))[1]).isEqualTo("Saúde");
    }

    @Test
    @DisplayName("tipos sem correspondência segura vão para Outros; locais que não são comércio ficam de fora")
    void semClassificacaoForcada() {
        assertThat(Categorias.pelasTagsOsm(Map.of("shop", "variety_store", "name", "Loja Girassol"))[1]).isEqualTo("Outros");
        assertThat(Categorias.pelasTagsOsm(Map.of("amenity", "school"))).isNull();
        assertThat(Categorias.pelasTagsOsm(Map.of("amenity", "place_of_worship"))).isNull();
        assertThat(Categorias.pelasTagsOsm(Map.of("leisure", "park"))).isNull();
    }

    @Test
    @DisplayName("classifica pelas palavras do nome")
    void pelaDescricao() {
        assertThat(Categorias.pelaDescricao("Restaurante X")).isEqualTo("Alimentação");
        assertThat(Categorias.pelaDescricao("Farmácia X")).isEqualTo("Saúde");
        assertThat(Categorias.pelaDescricao("Supermercado X")).isEqualTo("Mercados");
        assertThat(Categorias.pelaDescricao("Oficina X")).isEqualTo("Automotivo");
        assertThat(Categorias.pelaDescricao("OFICINA DE COSTURA X")).isEqualTo("Moda e Beleza");
        assertThat(Categorias.pelaDescricao("MERCEARIA BAR X")).isEqualTo("Mercados");
        assertThat(Categorias.pelaDescricao("CICINHO CELULAR")).isEqualTo("Tecnologia");
        assertThat(Categorias.pelaDescricao("SERRALHERIA LAURA")).isEqualTo("Casa e Construção");
        assertThat(Categorias.pelaDescricao("ESCRITORIO O P CONSIG")).isEqualTo("Serviços");
        assertThat(Categorias.pelaDescricao("LOJA NANA NENE")).isNull();
    }
}
