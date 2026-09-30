package br.com.rumocap.levantamento;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.rumocap.levantamento.Duplicados.Perfil;
import br.com.rumocap.levantamento.Duplicados.Semelhanca;
import br.com.rumocap.model.Categoria;
import br.com.rumocap.model.Estabelecimento;

/**
 * Os nomes abaixo são apenas textos comparados em memória (nada é gravado).
 * Os exemplos "Restaurante do João" / "Restaurante João" são os do enunciado do projeto.
 */
@DisplayName("Controle de duplicados")
class DuplicadosTest {

    @Test
    @DisplayName("nomes com e sem conectivos são considerados iguais")
    void nomesSemelhantes() {
        assertThat(Similaridade.nomes("Restaurante do João", "Restaurante João")).isEqualTo(1.0);
        assertThat(Similaridade.nomes("PRO DENT", "Dental Pro Dent")).isEqualTo(1.0);
        assertThat(Similaridade.nomes("Mercadinho das Meninas", "MERCADINHO DAS MENINAS")).isEqualTo(1.0);
        assertThat(Similaridade.nomes("Bar do Célio", "Bar do Mário")).isLessThan(0.6);
        assertThat(Similaridade.nomes("Friobom Sorvete", "Friobom Sorveteria")).isGreaterThanOrEqualTo(0.85);
    }

    @Test
    @DisplayName("só o tipo de negócio em comum não torna dois nomes parecidos")
    void tipoEmComum() {
        // vizinhos reais do Censo 2022: estabelecimentos diferentes lado a lado
        assertThat(Similaridade.nomes("Lanchonete Diza", "Lanchonete Kedy")).isLessThan(0.6);
        assertThat(Similaridade.nomes("Gomes Motopeças", "Emily Moto Peças")).isLessThan(0.6);
        // mesmo nome próprio, mas tipos de negócio diferentes
        assertThat(Similaridade.nomes("Mercadinho Bom Jesus", "Panificadora Bom Jesus")).isLessThan(0.85);
        // nomes oficiais que só repetem o nome do município
        assertThat(Similaridade.nomes("Academia da Saúde de Capitão Poço", "Secretaria Municipal de Saúde de Capitão Poço"))
                .isLessThan(0.6);
    }

    @Test
    @DisplayName("endereços iguais escritos de formas diferentes são reconhecidos")
    void enderecos() {
        assertThat(Similaridade.enderecos("Trav. Abdias Pereira, 230 - Tatajuba", "Travessa Abdias Pereira, 230 - Centro"))
                .isGreaterThanOrEqualTo(0.9);
        assertThat(Similaridade.enderecos("Travessa Abdias Pereira, 230", "Travessa Abdias Pereira, 734")).isZero();
    }

    @Test
    @DisplayName("nome semelhante e local próximo indicam possível duplicado")
    void nomeEProximidade() {
        Estabelecimento existente = estabelecimento("Restaurante do João", -1.7447, -47.0638, null, "OpenStreetMap");
        Duplicados indice = new Duplicados(List.of(existente));

        Perfil perto = new Perfil("Restaurante João", null, null, null, -1.7449, -47.0639, "IBGE");
        assertThat(indice.maisSemelhante(perto, null)).isPresent()
                .get().extracting(Semelhanca::existente).isSameAs(existente);

        Perfil longe = new Perfil("Restaurante João", null, null, null, -1.9000, -47.2000, "IBGE");
        assertThat(indice.maisSemelhante(longe, null)).isEmpty();
    }

    @Test
    @DisplayName("o mesmo telefone também indica possível duplicado, mesmo sem localização")
    void mesmoTelefone() {
        Estabelecimento existente = estabelecimento("DK Farma", null, null, "(91) 98578-5859", "IBGE");
        Duplicados indice = new Duplicados(List.of(existente));

        Perfil perfil = new Perfil("Drogaria DK Farma", null, "+55 91 98578 5859", null, null, null, "OpenStreetMap");
        Semelhanca semelhanca = indice.maisSemelhante(perfil, null).orElseThrow();
        assertThat(semelhanca.mesmoTelefone()).isTrue();
        assertThat(semelhanca.motivo()).contains("mesmo telefone");
    }

    @Test
    @DisplayName("repetição exata na mesma fonte é reconhecida (não vira novo cadastro)")
    void repeticaoNaMesmaFonte() {
        Estabelecimento existente = estabelecimento("Funerária Paz Eterna", -1.748322, -47.062703, null, "IBGE");
        Semelhanca mesmaFonte = Duplicados.comparar(
                new Perfil("Funerária Paz Eterna", null, null, null, -1.748330, -47.062710, "IBGE"),
                Perfil.de(existente), existente);
        assertThat(mesmaFonte.repeticaoNaMesmaFonte()).isTrue();

        Semelhanca outraFonte = Duplicados.comparar(
                new Perfil("Funerária Paz Eterna", null, null, null, -1.748330, -47.062710, "OpenStreetMap"),
                Perfil.de(existente), existente);
        assertThat(outraFonte.repeticaoNaMesmaFonte()).isFalse();
        assertThat(outraFonte.provavel()).isTrue();
    }

    private static Estabelecimento estabelecimento(String nome, Double latitude, Double longitude, String telefone,
                                                   String fonte) {
        Estabelecimento estabelecimento = new Estabelecimento();
        estabelecimento.setNome(nome);
        estabelecimento.setCategoria(new Categoria("Outros"));
        estabelecimento.definirLocalizacao(latitude, longitude);
        estabelecimento.setTelefone(telefone);
        estabelecimento.setFonte(fonte);
        return estabelecimento;
    }
}
