package br.com.rumocap.levantamento;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import br.com.rumocap.util.TextoUtils;

/**
 * Classificação automática nas categorias do RumoCap.
 * <p>
 * Usa o tipo informado pela fonte (tags do OpenStreetMap) ou palavras-chave do
 * nome/descrição (CNEFE). Quando não há segurança, o resultado é "Outros":
 * nenhuma classificação é forçada.
 */
public final class Categorias {

    public static final String ALIMENTACAO = "Alimentação";
    public static final String SAUDE = "Saúde";
    public static final String MERCADOS = "Mercados";
    public static final String MODA_E_BELEZA = "Moda e Beleza";
    public static final String TECNOLOGIA = "Tecnologia";
    public static final String SERVICOS = "Serviços";
    public static final String CASA_E_CONSTRUCAO = "Casa e Construção";
    public static final String AUTOMOTIVO = "Automotivo";
    public static final String OUTROS = "Outros";

    private Categorias() {
    }

    /* ============================ OpenStreetMap ============================ */

    private static final Map<String, Map<String, String>> OSM = new LinkedHashMap<>();

    private static void osm(String chave, String categoria, String... valores) {
        Map<String, String> porValor = OSM.computeIfAbsent(chave, k -> new LinkedHashMap<>());
        for (String valor : valores) {
            porValor.put(valor, categoria);
        }
    }

    static {
        osm("amenity", ALIMENTACAO, "restaurant", "fast_food", "cafe", "bar", "pub", "ice_cream", "food_court",
                "biergarten", "nightclub");
        osm("amenity", SAUDE, "pharmacy", "hospital", "clinic", "doctors", "dentist");
        osm("amenity", MERCADOS, "marketplace");
        osm("amenity", TECNOLOGIA, "internet_cafe");
        osm("amenity", SERVICOS, "bank", "bureau_de_change", "money_transfer", "payment_centre", "post_office",
                "veterinary", "driving_school", "townhall", "police", "courthouse", "fire_station", "cinema");
        osm("amenity", AUTOMOTIVO, "fuel", "car_wash", "car_rental", "vehicle_inspection", "motorcycle_rental");

        osm("shop", ALIMENTACAO, "bakery", "pastry", "confectionery", "chocolate", "coffee", "tea", "ice_cream");
        osm("shop", SAUDE, "chemist", "optician", "medical_supply", "hearing_aids", "herbalist",
                "nutrition_supplements");
        osm("shop", MERCADOS, "supermarket", "convenience", "grocery", "greengrocer", "butcher", "general",
                "wholesale", "farm", "seafood", "beverages", "alcohol", "frozen_food", "dairy", "deli", "cheese",
                "spices", "water", "food", "health_food");
        osm("shop", MODA_E_BELEZA, "clothes", "shoes", "boutique", "fashion", "fashion_accessories", "jewelry",
                "bag", "beauty", "hairdresser", "hairdresser_supply", "cosmetics", "perfumery", "fabric", "tailor",
                "watches", "leather", "sewing", "massage", "tattoo", "wool");
        osm("shop", TECNOLOGIA, "electronics", "computer", "mobile_phone", "telecommunication", "video_games",
                "hifi", "radiotechnics", "camera");
        osm("shop", SERVICOS, "laundry", "dry_cleaning", "copyshop", "funeral_directors", "travel_agency",
                "photo", "printing", "pet_grooming", "lottery", "ticket", "rental", "money_lender", "pawnbroker",
                "insurance");
        osm("shop", CASA_E_CONSTRUCAO, "hardware", "doityourself", "trade", "paint", "furniture", "houseware",
                "interior_decoration", "electrical", "bathroom_furnishing", "kitchen", "appliance", "garden_centre",
                "flooring", "tiles", "doors", "windows", "curtain", "bed", "carpet", "lighting", "glaziery",
                "building_materials", "security", "fireplace");
        osm("shop", AUTOMOTIVO, "car", "car_repair", "car_parts", "motorcycle", "motorcycle_repair", "tyres",
                "bicycle", "fuel", "truck", "atv", "car_accessories");

        osm("craft", MODA_E_BELEZA, "tailor", "dressmaker", "jeweller");
        osm("craft", TECNOLOGIA, "electronics_repair");
        osm("craft", SERVICOS, "photographer", "key_cutter", "locksmith", "shoemaker", "watchmaker", "printer",
                "sign_maker", "bookbinder");
        osm("craft", CASA_E_CONSTRUCAO, "carpenter", "electrician", "plumber", "builder", "painter",
                "metal_construction", "stonemason", "glaziery", "roofer", "tiler", "window_construction", "hvac",
                "joiner", "cabinet_maker", "upholsterer", "floorer", "plasterer", "sawmill");
        osm("craft", AUTOMOTIVO, "car_repair", "mechanic");

        osm("office", TECNOLOGIA, "it", "telecommunication");
        osm("tourism", SERVICOS, "hotel", "guest_house", "motel", "hostel", "apartment", "chalet");
        osm("leisure", SERVICOS, "fitness_centre");
    }

    /** Escritórios que não são estabelecimentos do guia. */
    private static final Set<String> ESCRITORIOS_IGNORADOS = Set.of("religion", "political_party", "association", "ngo");

    /**
     * Categoria pelas tags do OpenStreetMap.
     *
     * @return {chave=valor usada, categoria}, ou {@code null} quando o local não é um estabelecimento do guia
     */
    public static String[] pelasTagsOsm(Map<String, String> tags) {
        for (String chave : List.of("amenity", "shop", "healthcare", "craft", "office", "tourism", "leisure")) {
            String valor = tags.get(chave);
            if (valor == null) {
                continue;
            }
            String tipo = chave + "=" + valor;
            if (chave.equals("healthcare")) {
                return new String[] {tipo, SAUDE};
            }
            if (chave.equals("office")) {
                if (ESCRITORIOS_IGNORADOS.contains(valor)) {
                    return null;
                }
                return new String[] {tipo, OSM.get("office").getOrDefault(valor, SERVICOS)};
            }
            String categoria = OSM.getOrDefault(chave, Map.of()).get(valor);
            if (categoria != null) {
                return new String[] {tipo, categoria};
            }
            if (chave.equals("shop") || chave.equals("craft")) {
                // loja ou ofício sem correspondência direta: tenta pelo nome, senão "Outros"
                String peloNome = pelaDescricao(tags.get("name"));
                return new String[] {tipo, peloNome != null ? peloNome : OUTROS};
            }
        }
        return null;
    }

    /** Nome em português dos tipos mais comuns do OpenStreetMap (usado como descrição curta). */
    private static final Map<String, String> ROTULOS_OSM = Map.ofEntries(
            Map.entry("amenity=restaurant", "Restaurante"), Map.entry("amenity=fast_food", "Lanchonete"),
            Map.entry("amenity=cafe", "Café"), Map.entry("amenity=bar", "Bar"), Map.entry("amenity=pub", "Bar"),
            Map.entry("amenity=ice_cream", "Sorveteria"), Map.entry("amenity=pharmacy", "Farmácia"),
            Map.entry("amenity=hospital", "Hospital"), Map.entry("amenity=clinic", "Clínica"),
            Map.entry("amenity=doctors", "Consultório médico"), Map.entry("amenity=dentist", "Dentista"),
            Map.entry("amenity=veterinary", "Clínica veterinária"), Map.entry("amenity=bank", "Banco"),
            Map.entry("amenity=post_office", "Agência dos Correios"), Map.entry("amenity=fuel", "Posto de combustível"),
            Map.entry("amenity=car_wash", "Lava-jato"), Map.entry("amenity=internet_cafe", "Acesso à internet"),
            Map.entry("amenity=townhall", "Órgão público municipal"), Map.entry("amenity=police", "Delegacia de polícia"),
            Map.entry("amenity=marketplace", "Feira / mercado público"), Map.entry("amenity=driving_school", "Autoescola"),
            Map.entry("shop=supermarket", "Supermercado"), Map.entry("shop=convenience", "Mercearia / conveniência"),
            Map.entry("shop=bakery", "Padaria"), Map.entry("shop=butcher", "Açougue"),
            Map.entry("shop=clothes", "Loja de roupas"), Map.entry("shop=shoes", "Loja de calçados"),
            Map.entry("shop=hairdresser", "Salão de beleza / barbearia"), Map.entry("shop=beauty", "Salão de beleza"),
            Map.entry("shop=mobile_phone", "Loja de celulares"), Map.entry("shop=electronics", "Loja de eletrônicos"),
            Map.entry("shop=computer", "Informática"), Map.entry("shop=hardware", "Ferragens"),
            Map.entry("shop=doityourself", "Material de construção"), Map.entry("shop=furniture", "Loja de móveis"),
            Map.entry("shop=car_repair", "Oficina mecânica"), Map.entry("shop=car_parts", "Autopeças"),
            Map.entry("shop=motorcycle", "Loja de motos"), Map.entry("shop=tyres", "Pneus"),
            Map.entry("shop=optician", "Ótica"), Map.entry("shop=chemist", "Drogaria"),
            Map.entry("shop=variety_store", "Loja de variedades"), Map.entry("shop=stationery", "Papelaria"),
            Map.entry("tourism=hotel", "Hotel"), Map.entry("tourism=guest_house", "Pousada"),
            Map.entry("tourism=motel", "Motel"), Map.entry("leisure=fitness_centre", "Academia"));

    /** Descrição curta do tipo do OpenStreetMap, por exemplo "amenity=pharmacy" -> "Farmácia". */
    public static String rotuloOsm(String tipo) {
        return tipo == null ? null : ROTULOS_OSM.get(tipo);
    }

    /* ======================== Palavras-chave (texto) ======================== */

    /** Regras em ordem de prioridade: a primeira categoria com uma palavra encontrada vence. */
    private static final Map<String, Pattern> PALAVRAS = new LinkedHashMap<>();

    private static void palavras(String categoria, String expressao) {
        PALAVRAS.put(categoria, Pattern.compile("\\b(?:" + expressao + ")\\b"));
    }

    static {
        palavras(SAUDE, "FARMACIA|FARMA|DROGARIA|DROGAS|CLINICA|CONSULTORIO|LABORATORIO|ODONTO\\w*|DENTAL"
                + "|DENTISTA|OTICA|OPTICA|FISIOTERAPIA|FISIO|PSICOLOG\\w*|MEDIC[OA]S?|MEDICINA|HOSPITAL|SAUDE"
                + "|VACINAS?|EXAMES|RADIOLOGIA|ULTRASSOM|PHARMA|PROTESE|PROTESES|MANIPULACAO|NUTRICIONISTA");
        palavras(AUTOMOTIVO, "POSTOS?|AUTO ?PECAS|MOTO ?PECAS|BORRACHARIA|LAVA ?JATO|PNEUS?|BATERIAS?|FUNILARIA"
                + "|CAPOTARIA|AUTO ?ELETRICA|RETIFICA|CONCESSIONARIA|DIESEL|LUBRIFICANTES?|OFICINA(?! DE (?:COSTURA|MOVEIS|ARTES?))|MECANICA"
                + "|BICICLETARIA|BICICLETAS?|RODAS|AUTO ?CENTER|AUTOSOM|EQUIPADORA|ESTACIONAMENTO|MOTOS"
                + "|PECAS|OLEO|OLEOS|VEICULOS");
        palavras(TECNOLOGIA, "CELULAR|CELULARES|CELL|SMARTPHONES?|INFORMATICA|COMPUTADOR(?:ES)?|ELETRONICA"
                + "|ELETRONICOS|LAN ?HOUSE|CIBER|CYBER|PROVEDOR|INTERNET|TELECOM|TECH|GAMES|ANTENAS?");
        palavras(MERCADOS, "SUPERMERCADO|MINIMERCADO|MERCADINHO|MERCADO|MERCEARIA|ATACADO|ATACADAO|ATACAREJO"
                + "|ACOUGUE|CASA DE CARNES?|CARNES|PEIXARIA|FRUTARIA|FRUTEIRA|HORTIFRUTI|QUITANDA|BEBIDAS"
                + "|CONVENIENCIA|FRIGORIFICO|FRANGOS?|AVICOLA|EMPORIO|ALIMENTOS|ARMAZEM|CESTAS? BASICAS?"
                + "|POLPAS?|MERCANTIL|OVOS");
        palavras(ALIMENTACAO, "RESTAURANTE|LANCHONETE|LANCHES|LANCHE|LANCHERIA|BAR|BOTECO|BUTECO|BOTEQUIM"
                + "|CHURRASCARIA|CHURRASCO|CHURRASQUINHO|ESPETINHOS?|PIZZARIA|PIZZAS?|SORVETERIA|SORVETES?|ACAI"
                + "|ACAITERIA|PADARIA|PANIFICADORA|CONFEITARIA|DOCERIA|BOLOS?|CAFE|CAFETERIA|HAMBURGUERIA"
                + "|BURGUER|BURGER|PASTELARIA|PASTEL|TAPIOCARIA|SUCOS?|JUICES?|CANTINA|SELF SERVICE|QUENTINHAS?"
                + "|MARMITARIA|MARMITAS?|COMIDAS?|PETISCARIA|CHOPERIA|SALGADOS|DOCES|CARAMELOS|TACACA"
                + "|CREPERIA|GELATERIA|PICOLES?|FRANGAO|ASSADOS|COZINHA|ICE CREAM");
        palavras(MODA_E_BELEZA, "SALAO DE BELEZA|BELEZA|BARBEARIA|BARBERSHOP|BARBER|CABELEIREIR[OA]|CABELOS?"
                + "|ESTETICA|MANICURE|PEDICURE|UNHAS|SOBRANCELHAS?|MAQUIAGEM|ROUPAS?|CONFECCAO|CONFECCOES"
                + "|BOUTIQUE|MODAS?|CALCADOS|SAPATARIA|SAPATOS|PERFUMARIA|PERFUMES|COSMETICOS|BIJUTERIAS?"
                + "|BIJOUX|JOIAS|JOALHERIA|RELOJOARIA|TECIDOS|AVIAMENTOS|JEANS|BRECHO|COSTURA|COSTUREIRA"
                + "|ALFAIATARIA|ATELIE|FASHION|MULTIMARCAS|LINGERIE|SALAO");
        palavras(CASA_E_CONSTRUCAO, "MATERIA(?:L|IS) DE CONSTRUCAO|CONSTRUCAO|CONSTRU\\w*|FERRAGENS|FERRAGEM"
                + "|FERRAGISTA|MOVELARIA|MOVEIS|MARCENARIA|SERRARIA|SERRALHERIA|VIDRACARIA|VIDROS|OLARIA"
                + "|TIJOLOS?|MADEIREIRA|MADEIRAS|MATERIAL ELETRICO|ELETRICA|HIDRAULICA|TINTAS|CERAMICA|PISOS"
                + "|REVESTIMENTOS|ELETRODOMESTICOS|ELETROS|COLCHOES|ESTOFADOS|DECORACAO|MARMORARIA|GRANITOS"
                + "|TELHAS|CIMENTO|REFORMAS|PRE ?MOLDADOS|TUBOS|UTILIDADES DOMESTICAS|CAMA MESA E BANHO"
                + "|REFRIGERACAO|CLIMATIZACAO|PORTOES|PORTAO|GRADES|METALURGICA");
        palavras(SERVICOS, "FUNERARIA|ESCRITORIO|CONTABILIDADES?|CONTABIL|CONTADOR|ADVOCACIA|ADVOGADOS?|CARTORIO|LOTERICA"
                + "|LOTERIAS|BANCO|CORRESPONDENTE|CREDITO|CONSIG\\w*|EMPRESTIMOS?|FINANCEIRA|LAVANDERIA|GRAFICA"
                + "|COPIADORA|XEROX|COPIAS|COPY|FOTOGRAFIA|FOTOS?|ACADEMIA|FITNESS|CROSSFIT|AUTO ?ESCOLA"
                + "|IMOBILIARIA|IMOVEIS|HOTEL|POUSADA|PENSAO|DORMITORIO|HOSPEDAGEM|MOTEL|VETERINARI\\w*"
                + "|PET ?SHOP|CHAVEIRO|SAPATEIRO|CONSERTOS?|DESPACHANTE|SEGUROS?|CURSOS?|EVENTOS|SERIGRAFIA"
                + "|COMUNICACAO VISUAL|PLACAS|CIRETRAN|DETRAN|CORREIOS|AGENCIA");
    }

    /**
     * Categoria pelas palavras de um nome ou descrição (ex.: "MERCADINHO BOM JESUS" -> Mercados).
     *
     * @return a categoria, ou {@code null} quando nenhuma palavra-chave foi encontrada
     */
    public static String pelaDescricao(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String normalizado = paraComparacao(texto);
        for (Map.Entry<String, Pattern> regra : PALAVRAS.entrySet()) {
            if (regra.getValue().matcher(normalizado).find()) {
                return regra.getKey();
            }
        }
        return null;
    }

    /** MAIÚSCULAS sem acentos e sem pontuação: "Lanchonete da Vó!" -> "LANCHONETE DA VO". */
    public static String paraComparacao(String texto) {
        return TextoUtils.normalizar(texto)
                .toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .strip();
    }
}
