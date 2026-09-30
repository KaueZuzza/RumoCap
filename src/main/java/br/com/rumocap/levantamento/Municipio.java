package br.com.rumocap.levantamento;

/**
 * Dados geográficos de Capitão Poço (PA) usados para conferir se um local
 * realmente pertence ao município.
 */
public final class Municipio {

    public static final String NOME = "Capitão Poço";

    /** Código do município no IBGE (7 dígitos). */
    public static final String CODIGO_IBGE = "1502301";

    /** Código do município usado pelo DATASUS/CNES (6 dígitos, sem o verificador). */
    public static final String CODIGO_DATASUS = "150230";

    /** Praça da Alvorada, centro da cidade. */
    public static final double CENTRO_LATITUDE = -1.7447;
    public static final double CENTRO_LONGITUDE = -47.0638;

    /*
     * Retângulo que envolve o limite oficial do município (malha do IBGE),
     * com cerca de 1 km de folga. Coordenadas fora dele certamente estão erradas.
     */
    private static final double OESTE = -47.53;
    private static final double LESTE = -46.915;
    private static final double NORTE = -1.525;
    private static final double SUL = -2.605;

    private static final double RAIO_DA_TERRA_METROS = 6_371_000;

    private Municipio() {
    }

    public static boolean contem(double latitude, double longitude) {
        return latitude <= NORTE && latitude >= SUL && longitude >= OESTE && longitude <= LESTE;
    }

    /** Distância aproximada até o centro da cidade, em metros. */
    public static double distanciaDoCentro(double latitude, double longitude) {
        return distanciaEmMetros(latitude, longitude, CENTRO_LATITUDE, CENTRO_LONGITUDE);
    }

    /** Distância entre dois pontos pela fórmula de Haversine, em metros. */
    public static double distanciaEmMetros(double latitude1, double longitude1, double latitude2, double longitude2) {
        double dLat = Math.toRadians(latitude2 - latitude1);
        double dLng = Math.toRadians(longitude2 - longitude1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * RAIO_DA_TERRA_METROS * Math.asin(Math.min(1, Math.sqrt(a)));
    }
}
