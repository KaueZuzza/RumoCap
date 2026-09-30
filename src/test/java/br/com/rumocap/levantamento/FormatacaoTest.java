package br.com.rumocap.levantamento;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Formatação dos dados das fontes")
class FormatacaoTest {

    @Test
    @DisplayName("nomes em maiúsculas ficam em formato de título, com acentos e siglas")
    void nomes() {
        assertThat(Capitalizacao.nome("CENTRO DE SAUDE DE CAPITAO POCO")).isEqualTo("Centro de Saúde de Capitão Poço");
        assertThat(Capitalizacao.nome("UNIDADE DE PRONTO ATENDIMENTO UPA DE CAPITAO POCO"))
                .isEqualTo("Unidade de Pronto Atendimento UPA de Capitão Poço");
        assertThat(Capitalizacao.nome("HOSPITAL DR ALDOMAR AARAO MONTEIRO")).isEqualTo("Hospital Dr. Aldomar Aarão Monteiro");
        assertThat(Capitalizacao.nome("PS VILA DO ACAITEUA")).isEqualTo("PS Vila do Acaiteua");
        assertThat(Capitalizacao.nome("CAPS1 ROSA DE CAPITAO POCO")).isEqualTo("CAPS1 Rosa de Capitão Poço");
        assertThat(Capitalizacao.nome("DK FARMA")).isEqualTo("DK Farma");
        assertThat(Capitalizacao.nome("W M PROTESE")).isEqualTo("W M Prótese");
        assertThat(Capitalizacao.nome("POSTO DE GASOLINA EP")).isEqualTo("Posto de Gasolina EP");
        assertThat(Capitalizacao.nome("CLINICA CIRURGICA E GENECOLOGICA DO PARA"))
                .isEqualTo("Clínica Cirúrgica e Genecologica do Pará");
    }

    @Test
    @DisplayName("nomes digitados com minúsculas não são alterados, só ganham a inicial maiúscula")
    void nomesDigitados() {
        assertThat(Capitalizacao.nome("hotel João Moura")).isEqualTo("Hotel João Moura");
        assertThat(Capitalizacao.nome("Oficina do cabelo barbershop")).isEqualTo("Oficina do cabelo barbershop");
    }

    @Test
    @DisplayName("endereços expandem abreviações e tratam número e bairro")
    void enderecos() {
        assertThat(Capitalizacao.endereco("TRAV ABDIAS PEREIRA", "230", "TATAJUBA"))
                .isEqualTo("Travessa Abdias Pereira, 230 - Tatajuba");
        assertThat(Capitalizacao.endereco("AV 29 DE DEZEMBRO", "1389", "CENTRO"))
                .isEqualTo("Avenida 29 de Dezembro, 1389 - Centro");
        assertThat(Capitalizacao.endereco("ACAITEUA", "S/N", "LOCALIDADE")).isEqualTo("Acaiteua, s/n");
        assertThat(Capitalizacao.endereco("VILA KENEDY", "S/N", "ZONA RURAL")).isEqualTo("Vila Kenedy, s/n - Zona rural");
        assertThat(Capitalizacao.endereco("Travessa José Barros da Silva", "338", "TATAJUBA"))
                .isEqualTo("Travessa José Barros da Silva, 338 - Tatajuba");
        assertThat(Capitalizacao.endereco("rodovia PA-124", null, "Vila Nova")).isEqualTo("Rodovia PA-124, s/n - Vila Nova");
        assertThat(Capitalizacao.endereco(null, "10", "Centro")).isNull();
    }

    @Test
    @DisplayName("telefones válidos são formatados; incompletos e antigos são recusados com explicação")
    void telefones() {
        assertThat(Telefones.analisar("+55 91 98578 5859").formatado()).isEqualTo("(91) 98578-5859");
        assertThat(Telefones.analisar("(91)34681888").formatado()).isEqualTo("(91) 3468-1888");
        assertThat(Telefones.analisar("09134681511").formatado()).isEqualTo("(91) 3468-1511");
        assertThat(Telefones.analisar("34681700").formatado()).isEqualTo("(91) 3468-1700");
        assertThat(Telefones.analisar("08000921920").formatado()).isEqualTo("0800 092 1920");
        assertThat(Telefones.analisar("(91)4005-5070").formatado()).isEqualTo("(91) 4005-5070");

        Telefones.Analise incompleto = Telefones.analisar("(09)14681491");
        assertThat(incompleto.valido()).isFalse();
        assertThat(incompleto.problema()).contains("incompleto");

        Telefones.Analise antigo = Telefones.analisar("91 82840048");
        assertThat(antigo.valido()).isFalse();
        assertThat(antigo.problema()).contains("nono dígito");

        assertThat(Telefones.analisar("919").valido()).isFalse();
        assertThat(Telefones.analisar(null).problema()).isNull();
    }

    @Test
    @DisplayName("horários do OpenStreetMap e turnos do CNES são traduzidos")
    void horarios() {
        assertThat(Horarios.deOpenStreetMap("Mo-Su 07:00-22:00")).isEqualTo("Todos os dias: 07:00–22:00");
        assertThat(Horarios.deOpenStreetMap("Mo-Sa 08:15-11:15; Mo-Sa 14:15-18:15"))
                .isEqualTo("Seg. a sáb.: 08:15–11:15 e 14:15–18:15");
        assertThat(Horarios.deOpenStreetMap("Mo-Fr 08:00-12:00,14:00-18:00; Sa 08:00-12:00; Su off"))
                .isEqualTo("Seg. a sex.: 08:00–12:00 e 14:00–18:00\nSáb.: 08:00–12:00\nDom.: fechado");
        assertThat(Horarios.deOpenStreetMap("24/7")).isEqualTo("Todos os dias, 24 horas");
        assertThat(Horarios.deOpenStreetMap("sunrise-sunset")).isEqualTo("sunrise-sunset");

        assertThat(Horarios.deCnes("ATENDIMENTOS NOS TURNOS DA MANHA E A TARDE")).isEqualTo("Turnos da manhã e da tarde");
        assertThat(Horarios.deCnes("ATENDIMENTO SOMENTE PELA MANHA")).isEqualTo("Somente pela manhã");
        assertThat(Horarios.deCnes("ATENDIMENTO CONTINUO DE 24 HORAS/DIA (PLANTAO:INCLUI SABADOS, DOMINGOS E FERIADOS)"))
                .startsWith("24 horas, todos os dias");
    }
}
