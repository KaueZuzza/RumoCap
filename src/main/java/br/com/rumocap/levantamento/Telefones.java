package br.com.rumocap.levantamento;

/**
 * Confere e formata telefones brasileiros vindos das fontes.
 * <p>
 * Nenhum dígito é inventado: números incompletos ou no formato antigo de
 * celular (sem o nono dígito) não são aproveitados, e o motivo vai para a
 * observação da revisão. A única complementação é o DDD 91 em números locais
 * de 8 ou 9 dígitos, pois todo o município usa esse DDD.
 */
public final class Telefones {

    private static final String DDD_LOCAL = "91";

    private Telefones() {
    }

    /**
     * @param formatado número pronto para exibir, ou {@code null} quando não foi aproveitado
     * @param problema  explicação para a revisão quando o número foi descartado
     */
    public record Analise(String formatado, String problema) {

        public boolean valido() {
            return formatado != null;
        }
    }

    public static Analise analisar(String original) {
        if (original == null || original.isBlank()) {
            return new Analise(null, null);
        }
        String digitos = original.replaceAll("\\D", "");

        if (digitos.matches("0[38]00\\d{7}")) { // 0800 000 0000 e 0300
            return new Analise(digitos.substring(0, 4) + " " + digitos.substring(4, 7) + " " + digitos.substring(7), null);
        }
        if (digitos.matches("(3003|3004|400[0-9]|4020|4062|4090)\\d{4}")) { // números nacionais (4004-0000)
            return new Analise(digitos.substring(0, 4) + "-" + digitos.substring(4), null);
        }

        digitos = digitos.replaceFirst("^0+", "");
        if ((digitos.length() == 12 || digitos.length() == 13) && digitos.startsWith("55")) {
            digitos = digitos.substring(2);
        }
        if (digitos.length() == 8 && digitos.matches("[2-5]\\d{7}")) {
            digitos = DDD_LOCAL + digitos; // telefone fixo local sem DDD
        } else if (digitos.length() == 9 && digitos.matches("9[5-9]\\d{7}")) {
            digitos = DDD_LOCAL + digitos; // celular local sem DDD
        }

        if (digitos.length() == 11 && digitos.matches("[1-9][1-9]9[5-9]\\d{7}")) {
            return new Analise("(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 7) + "-" + digitos.substring(7), null);
        }
        if (digitos.length() == 10 && digitos.matches("[1-9][1-9][2-5]\\d{7}")) {
            return new Analise("(" + digitos.substring(0, 2) + ") " + digitos.substring(2, 6) + "-" + digitos.substring(6), null);
        }
        if (digitos.length() == 10 && digitos.matches("[1-9][1-9][6-9]\\d{7}")) {
            return new Analise(null, "Telefone da fonte está no formato antigo de celular, sem o nono dígito: "
                    + original.strip() + ". Confirme o número atual.");
        }
        return new Analise(null, "Telefone da fonte parece incompleto ou inválido: " + original.strip() + ".");
    }

    /** Últimos 8 dígitos, usados para reconhecer o mesmo telefone escrito de formas diferentes. */
    public static String chave(String telefone) {
        if (telefone == null) {
            return null;
        }
        String digitos = telefone.replaceAll("\\D", "");
        return digitos.length() >= 8 ? digitos.substring(digitos.length() - 8) : null;
    }
}
