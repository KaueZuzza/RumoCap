package br.com.rumocap.util;

import java.text.Collator;
import java.text.Normalizer;
import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Pattern;

/** Funções de texto usadas nas buscas e na ordenação. */
public final class TextoUtils {

    private static final Locale PORTUGUES_BRASIL = Locale.of("pt", "BR");
    private static final Pattern ACENTOS = Pattern.compile("\\p{M}+");
    private static final Pattern ESPACOS = Pattern.compile("\\s+");

    private TextoUtils() {
    }

    /**
     * Remove acentos, espaços repetidos e converte para minúsculas.
     * Assim, "Saúde" e "saude" são considerados iguais nas buscas.
     */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String semAcentos = ACENTOS.matcher(Normalizer.normalize(texto, Normalizer.Form.NFD)).replaceAll("");
        return ESPACOS.matcher(semAcentos.toLowerCase(Locale.ROOT).strip()).replaceAll(" ");
    }

    /** Remove os espaços das pontas e devolve {@code null} quando o texto fica vazio. */
    public static String limpar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.strip();
        return limpo.isEmpty() ? null : limpo;
    }

    /** Ordem alfabética do português, sem diferenciar maiúsculas de minúsculas. */
    public static Comparator<String> ordemAlfabetica() {
        Collator collator = Collator.getInstance(PORTUGUES_BRASIL);
        collator.setStrength(Collator.SECONDARY);
        return collator::compare;
    }
}
