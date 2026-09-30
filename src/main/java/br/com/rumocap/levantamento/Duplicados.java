package br.com.rumocap.levantamento;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import br.com.rumocap.model.Estabelecimento;

/**
 * Procura estabelecimentos já cadastrados que podem ser o mesmo local de um
 * novo registro, comparando nome, endereço, telefone e distância.
 * <p>
 * Para não comparar todos com todos, os cadastros ficam em um índice por
 * região do mapa (quadrados de cerca de 330 m), por palavra do nome e por telefone.
 */
public final class Duplicados {

    /** Tamanho da célula da grade, em graus (~330 m no Equador). */
    private static final double CELULA = 0.003;

    /** Palavras frequentes demais para ajudar a encontrar semelhantes pelo nome. */
    private static final Set<String> PALAVRAS_COMUNS = Set.of(
            "bar", "loja", "mercadinho", "mercado", "lanchonete", "oficina", "restaurante", "farmacia",
            "comercio", "casa", "ponto", "salao", "padaria", "panificadora", "distribuidora", "variedades",
            "moda", "modas", "centro", "capitao", "poco", "vila", "novo", "nova", "sao", "santa", "santo");

    private final Map<String, Estabelecimento> porFonteId = new HashMap<>();
    private final Map<Long, List<Estabelecimento>> grade = new HashMap<>();
    private final Map<String, List<Estabelecimento>> porPalavra = new HashMap<>();
    private final Map<String, List<Estabelecimento>> porTelefone = new HashMap<>();

    public Duplicados(Collection<Estabelecimento> cadastrados) {
        cadastrados.forEach(this::adicionar);
    }

    public void adicionar(Estabelecimento estabelecimento) {
        if (estabelecimento.getFonteId() != null) {
            porFonteId.put(estabelecimento.getFonteId(), estabelecimento);
        }
        if (estabelecimento.possuiLocalizacao()) {
            grade.computeIfAbsent(celula(estabelecimento.getLatitude(), estabelecimento.getLongitude()),
                    chave -> new ArrayList<>()).add(estabelecimento);
        }
        for (String palavra : palavrasDeBusca(estabelecimento.getNome())) {
            porPalavra.computeIfAbsent(palavra, chave -> new ArrayList<>()).add(estabelecimento);
        }
        for (String telefone : telefones(estabelecimento.getTelefone(), estabelecimento.getWhatsapp())) {
            porTelefone.computeIfAbsent(telefone, chave -> new ArrayList<>()).add(estabelecimento);
        }
    }

    public Optional<Estabelecimento> porFonteId(String fonteId) {
        return Optional.ofNullable(fonteId == null ? null : porFonteId.get(fonteId));
    }

    /**
     * O cadastro mais parecido com o perfil informado, se houver algum que
     * mereça atenção (possível duplicado).
     *
     * @param ignorarId id que não deve ser comparado (o próprio registro, na edição)
     */
    public Optional<Semelhanca> maisSemelhante(Perfil perfil, Long ignorarId) {
        Semelhanca melhor = null;
        for (Estabelecimento existente : vizinhos(perfil)) {
            if (existente.getId() != null && existente.getId().equals(ignorarId)) {
                continue;
            }
            Semelhanca semelhanca = comparar(perfil, Perfil.de(existente), existente);
            if (semelhanca.provavel() && (melhor == null || semelhanca.pontuacao() > melhor.pontuacao())) {
                melhor = semelhanca;
            }
        }
        return Optional.ofNullable(melhor);
    }

    /** Todos os cadastros semelhantes (usado na verificação antes de um cadastro manual). */
    public List<Semelhanca> semelhantes(Perfil perfil, Long ignorarId) {
        List<Semelhanca> lista = new ArrayList<>();
        for (Estabelecimento existente : vizinhos(perfil)) {
            if (existente.getId() != null && existente.getId().equals(ignorarId)) {
                continue;
            }
            Semelhanca semelhanca = comparar(perfil, Perfil.de(existente), existente);
            if (semelhanca.provavel()) {
                lista.add(semelhanca);
            }
        }
        lista.sort((a, b) -> Double.compare(b.pontuacao(), a.pontuacao()));
        return lista;
    }

    private Collection<Estabelecimento> vizinhos(Perfil perfil) {
        Set<Estabelecimento> encontrados = new LinkedHashSet<>();
        if (perfil.possuiLocalizacao()) {
            long linha = (long) Math.floor(perfil.latitude() / CELULA);
            long coluna = (long) Math.floor(perfil.longitude() / CELULA);
            for (long dLinha = -1; dLinha <= 1; dLinha++) {
                for (long dColuna = -1; dColuna <= 1; dColuna++) {
                    encontrados.addAll(grade.getOrDefault(chave(linha + dLinha, coluna + dColuna), List.of()));
                }
            }
        }
        for (String palavra : palavrasDeBusca(perfil.nome())) {
            encontrados.addAll(porPalavra.getOrDefault(palavra, List.of()));
        }
        for (String telefone : telefones(perfil.telefone(), perfil.whatsapp())) {
            encontrados.addAll(porTelefone.getOrDefault(telefone, List.of()));
        }
        return encontrados;
    }

    /** Compara dois perfis e explica o que eles têm em comum. */
    public static Semelhanca comparar(Perfil novo, Perfil existentePerfil, Estabelecimento existente) {
        double nome = Similaridade.nomes(novo.nome(), existentePerfil.nome());
        Double distancia = novo.possuiLocalizacao() && existentePerfil.possuiLocalizacao()
                ? Municipio.distanciaEmMetros(novo.latitude(), novo.longitude(),
                        existentePerfil.latitude(), existentePerfil.longitude())
                : null;
        double endereco = Similaridade.enderecos(novo.endereco(), existentePerfil.endereco());
        Set<String> telefonesNovos = new LinkedHashSet<>(telefones(novo.telefone(), novo.whatsapp()));
        telefonesNovos.retainAll(telefones(existentePerfil.telefone(), existentePerfil.whatsapp()));
        boolean mesmoTelefone = !telefonesNovos.isEmpty();
        boolean mesmaFonte = novo.fonte() != null && novo.fonte().equals(existentePerfil.fonte());
        return new Semelhanca(existente, nome, distancia, endereco, mesmoTelefone, mesmaFonte,
                novo.endereco() == null || existentePerfil.endereco() == null);
    }

    private static List<String> palavrasDeBusca(String nome) {
        return Similaridade.palavras(nome).stream()
                .filter(palavra -> palavra.length() >= 3 && !PALAVRAS_COMUNS.contains(palavra))
                .distinct()
                .toList();
    }

    private static List<String> telefones(String... numeros) {
        List<String> chaves = new ArrayList<>();
        for (String numero : numeros) {
            String chave = Telefones.chave(numero);
            if (chave != null) {
                chaves.add(chave);
            }
        }
        return chaves;
    }

    private static long celula(double latitude, double longitude) {
        return chave((long) Math.floor(latitude / CELULA), (long) Math.floor(longitude / CELULA));
    }

    private static long chave(long linha, long coluna) {
        return (linha << 32) ^ (coluna & 0xffffffffL);
    }

    /** Dados usados na comparação, vindos de um candidato ou de um cadastro. */
    public record Perfil(String nome, String endereco, String telefone, String whatsapp,
                         Double latitude, Double longitude, String fonte) {

        public static Perfil de(Estabelecimento estabelecimento) {
            return new Perfil(estabelecimento.getNome(), estabelecimento.getEndereco(), estabelecimento.getTelefone(),
                    estabelecimento.getWhatsapp(), estabelecimento.getLatitude(), estabelecimento.getLongitude(),
                    estabelecimento.getFonte());
        }

        public static Perfil de(Candidato candidato, String fonte) {
            return new Perfil(candidato.getNome(), candidato.getEndereco(), candidato.getTelefone(),
                    candidato.getWhatsapp(), candidato.getLatitude(), candidato.getLongitude(), fonte);
        }

        boolean possuiLocalizacao() {
            return latitude != null && longitude != null;
        }
    }

    /**
     * Resultado da comparação com um cadastro existente.
     *
     * @param nome            semelhança dos nomes (0 a 1)
     * @param distancia       distância em metros, quando os dois têm localização
     * @param endereco        semelhança dos endereços (0 a 1)
     * @param enderecoAusente um dos dois não tem endereço
     */
    public record Semelhanca(Estabelecimento existente, double nome, Double distancia, double endereco,
                             boolean mesmoTelefone, boolean mesmaFonte, boolean enderecoAusente) {

        /** Há motivo para desconfiar que é o mesmo local: vai para a revisão. */
        public boolean provavel() {
            if (mesmoTelefone && nome >= 0.3) {
                return true;
            }
            // o mesmo nome em outra fonte, dentro da cidade, pode ser o mesmo local em endereço antigo
            boolean mesmoNomeEmOutraFonte = !mesmaFonte && nome >= 0.95;
            if (nome >= 0.85) {
                if (distancia != null) {
                    return distancia <= 250 || (mesmoNomeEmOutraFonte && distancia <= 3000);
                }
                return endereco >= 0.85 || enderecoAusente || mesmoNomeEmOutraFonte;
            }
            if (nome >= 0.75 && distancia != null && distancia <= 40) {
                return true;
            }
            return endereco >= 0.9 && nome >= 0.5;
        }

        /** O mesmo local repetido dentro da própria fonte: não vira um novo cadastro. */
        public boolean repeticaoNaMesmaFonte() {
            return mesmaFonte && nome >= 0.95
                    && (distancia != null ? distancia <= 30 : endereco >= 0.95);
        }

        double pontuacao() {
            double proximidade = distancia == null ? 0 : Math.max(0, 1 - distancia / 250);
            return nome + endereco + proximidade + (mesmoTelefone ? 1 : 0);
        }

        /** Explicação curta: "nome 92% semelhante, a 35 m, mesmo telefone". */
        public String motivo() {
            List<String> partes = new ArrayList<>();
            partes.add(nome >= 0.99 ? "mesmo nome" : "nome " + Math.round(nome * 100) + "% semelhante");
            if (distancia != null) {
                partes.add(distancia < 1 ? "no mesmo ponto do mapa" : "a " + distanciaTexto(distancia));
            }
            if (endereco >= 0.9) {
                partes.add("mesmo endereço");
            }
            if (mesmoTelefone) {
                partes.add("mesmo telefone");
            }
            return String.join(", ", partes);
        }

        private static String distanciaTexto(double metros) {
            return metros < 1000
                    ? Math.round(metros) + " m"
                    : String.format(Locale.of("pt", "BR"), "%.1f km", metros / 1000);
        }
    }
}
