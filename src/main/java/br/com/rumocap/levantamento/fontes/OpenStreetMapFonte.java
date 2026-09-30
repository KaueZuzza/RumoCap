package br.com.rumocap.levantamento.fontes;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import br.com.rumocap.levantamento.Candidato;
import br.com.rumocap.levantamento.Capitalizacao;
import br.com.rumocap.levantamento.Categorias;
import br.com.rumocap.levantamento.ClienteHttp;
import br.com.rumocap.levantamento.Coleta;
import br.com.rumocap.levantamento.FonteDeDados;
import br.com.rumocap.levantamento.FonteIndisponivelException;
import br.com.rumocap.levantamento.Horarios;
import br.com.rumocap.levantamento.Municipio;
import br.com.rumocap.levantamento.Telefones;
import br.com.rumocap.util.TextoUtils;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * OpenStreetMap, consultado pela API Overpass.
 * <p>
 * A consulta usa a área oficial do município (código IBGE 1502301), então todo
 * local devolvido fica dentro de Capitão Poço. Só entram locais com nome e com
 * um tipo comercial ou de serviço (lojas, restaurantes, farmácias, oficinas...).
 */
@Component
@Order(1)
public class OpenStreetMapFonte implements FonteDeDados {

    private static final Logger log = LoggerFactory.getLogger(OpenStreetMapFonte.class);

    /** Servidores públicos da API Overpass, na ordem de tentativa (se um estiver sobrecarregado, tenta o próximo). */
    private static final List<URI> SERVIDORES = List.of(
            URI.create("https://overpass-api.de/api/interpreter"),
            URI.create("https://overpass.private.coffee/api/interpreter"),
            URI.create("https://maps.mail.ru/osm/tools/overpass/api/interpreter"));

    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(60);

    static final String CONSULTA = """
            [out:json][timeout:50];
            area["boundary"="administrative"]["IBGE:GEOCODIGO"="%s"]->.municipio;
            (
              nwr["name"]["shop"](area.municipio);
              nwr["name"]["amenity"](area.municipio);
              nwr["name"]["craft"](area.municipio);
              nwr["name"]["office"](area.municipio);
              nwr["name"]["healthcare"](area.municipio);
              nwr["name"]["tourism"](area.municipio);
              nwr["name"]["leisure"="fitness_centre"](area.municipio);
            );
            out center tags;
            """.formatted(Municipio.CODIGO_IBGE);

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ClienteHttp http;

    public OpenStreetMapFonte(ClienteHttp http) {
        this.http = http;
    }

    @Override
    public String chave() {
        return "osm";
    }

    @Override
    public String nome() {
        return "OpenStreetMap";
    }

    @Override
    public String descricao() {
        return "Mapa colaborativo mundial. Traz comércios e serviços mapeados dentro do limite oficial do "
                + "município, com endereço, telefone, site e horário quando os colaboradores informaram. "
                + "Somente locais com nome são considerados; escolas, igrejas e praças ficam de fora.";
    }

    @Override
    public String licenca() {
        return "© Contribuidores do OpenStreetMap, licença ODbL";
    }

    @Override
    public String endereco() {
        return "https://www.openstreetmap.org/relation/2702845";
    }

    @Override
    public Coleta coletar() {
        return interpretar(consultar());
    }

    private String consultar() {
        List<String> falhas = new ArrayList<>();
        for (URI servidor : SERVIDORES) {
            try {
                return http.enviarFormulario(servidor, Map.of("data", CONSULTA), TEMPO_LIMITE);
            } catch (FonteIndisponivelException erro) {
                log.warn("Overpass indisponível em {}: {}", servidor.getHost(), erro.getMessage());
                falhas.add(erro.getMessage());
            }
        }
        throw new FonteIndisponivelException("Nenhum servidor da API Overpass (OpenStreetMap) respondeu agora. "
                + String.join(" ", falhas));
    }

    /** Converte a resposta JSON da Overpass em candidatos. */
    Coleta interpretar(String resposta) {
        JsonNode raiz;
        try {
            raiz = JSON.readTree(resposta);
        } catch (JacksonException erro) {
            throw new FonteIndisponivelException("A API Overpass devolveu uma resposta que não é JSON.", erro);
        }
        JsonNode elementos = raiz.path("elements");
        if (!elementos.isArray()) {
            String aviso = raiz.path("remark").asString("");
            throw new FonteIndisponivelException(aviso.isBlank()
                    ? "A API Overpass devolveu uma resposta inesperada."
                    : "A API Overpass não concluiu a consulta: " + aviso);
        }

        Coleta coleta = new Coleta();
        for (JsonNode elemento : elementos.values()) {
            Map<String, String> tags = new LinkedHashMap<>();
            elemento.path("tags").properties().forEach(tag -> tags.put(tag.getKey(), tag.getValue().asString()));

            String nome = TextoUtils.limpar(tags.get("name"));
            if (nome == null) {
                coleta.descartar("Sem nome no OpenStreetMap");
                continue;
            }
            String[] tipoECategoria = Categorias.pelasTagsOsm(tags);
            if (tipoECategoria == null) {
                coleta.descartar("Não é comércio ou serviço (escola, igreja, praça, órgão sem atendimento...)");
                continue;
            }
            if ("disused".equals(tags.get("shop")) || tags.keySet().stream().anyMatch(chave -> chave.startsWith("disused:"))) {
                coleta.descartar("Marcado como desativado no OpenStreetMap");
                continue;
            }
            coleta.adicionar(candidato(elemento, tags, nome, tipoECategoria));
        }
        return coleta;
    }

    private Candidato candidato(JsonNode elemento, Map<String, String> tags, String nome, String[] tipoECategoria) {
        String tipo = elemento.path("type").asString();
        long id = elemento.path("id").asLong();
        Candidato candidato = new Candidato("osm:" + tipo + "/" + id, Capitalizacao.nome(nome), tipoECategoria[1]);
        candidato.setUrlFonte("https://www.openstreetmap.org/" + tipo + "/" + id);
        candidato.setTipoFonte(tipoECategoria[0]);
        String descricao = TextoUtils.limpar(tags.get("description"));
        candidato.setDescricao(limitar(descricao != null ? descricao : Categorias.rotuloOsm(tipoECategoria[0]), 400));
        candidato.setEndereco(endereco(tags));
        candidato.setHorario(limitar(Horarios.deOpenStreetMap(tags.get("opening_hours")), 500));

        preencherContatos(candidato, tags);
        preencherLocalizacao(candidato, elemento);

        String cidade = TextoUtils.limpar(tags.get("addr:city"));
        if (cidade != null && !TextoUtils.normalizar(cidade).equals(TextoUtils.normalizar(Municipio.NOME))) {
            candidato.observar("No OpenStreetMap, a cidade do endereço está como \"" + cidade + "\".");
        }
        tags.forEach(candidato::guardar);
        return candidato;
    }

    private static String endereco(Map<String, String> tags) {
        String completo = TextoUtils.limpar(tags.get("addr:full"));
        String rua = TextoUtils.limpar(tags.get("addr:street"));
        if (rua == null) {
            return completo == null ? null : limitar(Capitalizacao.nome(completo), 255);
        }
        String bairro = primeiro(tags, "addr:suburb", "addr:neighbourhood", "addr:quarter", "addr:place", "addr:hamlet");
        return limitar(Capitalizacao.endereco(rua, tags.get("addr:housenumber"), bairro), 255);
    }

    private static void preencherContatos(Candidato candidato, Map<String, String> tags) {
        List<String> outros = new ArrayList<>();

        List<String> telefones = new ArrayList<>();
        for (String chave : List.of("phone", "contact:phone", "contact:mobile", "mobile")) {
            String valor = tags.get(chave);
            if (valor != null) {
                for (String numero : valor.split("[;,/]")) {
                    if (!numero.isBlank()) {
                        telefones.add(numero.strip());
                    }
                }
            }
        }
        for (String numero : telefones) {
            Telefones.Analise analise = Telefones.analisar(numero);
            if (!analise.valido()) {
                candidato.observar(analise.problema());
            } else if (candidato.getTelefone() == null) {
                candidato.setTelefone(analise.formatado());
            } else if (!candidato.getTelefone().equals(analise.formatado())) {
                outros.add("Telefone: " + analise.formatado());
            }
        }

        String whatsapp = primeiro(tags, "contact:whatsapp", "whatsapp");
        if (whatsapp != null) {
            Telefones.Analise analise = Telefones.analisar(whatsapp);
            if (analise.valido()) {
                candidato.setWhatsapp(analise.formatado());
            } else {
                candidato.observar(analise.problema());
            }
        }

        String site = link(primeiro(tags, "website", "contact:website", "url"), null);
        String instagram = link(primeiro(tags, "contact:instagram", "instagram"), "https://www.instagram.com/");
        String facebook = link(primeiro(tags, "contact:facebook", "facebook"), "https://www.facebook.com/");
        if (site == null) {
            site = instagram != null ? instagram : facebook;
        }
        candidato.setSite(limitar(site, 255));
        if (instagram != null && !instagram.equals(site)) {
            outros.add(instagram);
        }
        if (facebook != null && !facebook.equals(site)) {
            outros.add(facebook);
        }

        String email = primeiro(tags, "email", "contact:email");
        if (email != null && email.contains("@")) {
            outros.add("E-mail: " + email.strip());
        }
        candidato.setContato(outros.isEmpty() ? null : limitar(String.join("\n", outros), 500));
    }

    /** Endereço de site completo; perfis informados só pelo nome ganham o endereço da rede. */
    private static String link(String valor, String prefixoDoPerfil) {
        String texto = TextoUtils.limpar(valor);
        if (texto == null || texto.contains(" ")) {
            return null;
        }
        if (texto.matches("(?i)https?://.+")) {
            return texto;
        }
        if (texto.matches("(?i)www\\..+|[\\w-]+(\\.[\\w-]+)+(/.*)?")) {
            return "https://" + texto;
        }
        if (prefixoDoPerfil != null && texto.matches("@?[\\w.]{1,40}")) {
            return prefixoDoPerfil + texto.replaceFirst("^@", "");
        }
        return null;
    }

    private static void preencherLocalizacao(Candidato candidato, JsonNode elemento) {
        JsonNode ponto = elemento.has("lat") ? elemento : elemento.path("center");
        if (!ponto.path("lat").isNumber() || !ponto.path("lon").isNumber()) {
            candidato.observar("O OpenStreetMap não informou a posição deste local.");
            return;
        }
        double latitude = ponto.path("lat").asDouble();
        double longitude = ponto.path("lon").asDouble();
        if (!Municipio.contem(latitude, longitude)) {
            candidato.observar("A posição informada fica fora do município e foi descartada.");
            return;
        }
        candidato.localizacao(latitude, longitude);
        if (!elemento.has("lat")) {
            candidato.observar("Local mapeado como área no OpenStreetMap: o marcador fica no centro da área.");
        }
    }

    private static String primeiro(Map<String, String> tags, String... chaves) {
        for (String chave : chaves) {
            String valor = TextoUtils.limpar(tags.get(chave));
            if (valor != null) {
                return valor;
            }
        }
        return null;
    }

    static String limitar(String texto, int maximo) {
        if (texto == null || texto.length() <= maximo) {
            return texto;
        }
        return texto.substring(0, maximo - 1).strip() + "…";
    }
}
