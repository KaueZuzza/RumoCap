package br.com.rumocap.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TextoUtils")
class TextoUtilsTest {

    @Test
    @DisplayName("normalizar remove acentos, espaços extras e maiúsculas")
    void normalizar() {
        assertThat(TextoUtils.normalizar("  Casa e   Construção ")).isEqualTo("casa e construcao");
        assertThat(TextoUtils.normalizar("SAÚDE")).isEqualTo("saude");
        assertThat(TextoUtils.normalizar(null)).isEmpty();
    }

    @Test
    @DisplayName("limpar remove espaços das pontas e transforma vazio em null")
    void limpar() {
        assertThat(TextoUtils.limpar("  Rua A  ")).isEqualTo("Rua A");
        assertThat(TextoUtils.limpar("linha 1\nlinha 2")).isEqualTo("linha 1\nlinha 2");
        assertThat(TextoUtils.limpar("   ")).isNull();
        assertThat(TextoUtils.limpar(null)).isNull();
    }

    @Test
    @DisplayName("ordemAlfabetica segue o português (acentos não jogam a palavra para o fim)")
    void ordemAlfabetica() {
        List<String> ordenados = Stream.of("Serviços", "Saúde", "automotivo", "Ótica", "Oficina")
                .sorted(TextoUtils.ordemAlfabetica())
                .toList();

        assertThat(ordenados).containsExactly("automotivo", "Oficina", "Ótica", "Saúde", "Serviços");
    }
}
