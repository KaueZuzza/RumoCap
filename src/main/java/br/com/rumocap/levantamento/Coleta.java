package br.com.rumocap.levantamento;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Resultado da consulta a uma fonte: os candidatos e os registros descartados, por motivo. */
public final class Coleta {

    private final List<Candidato> candidatos = new ArrayList<>();
    private final Map<String, Integer> descartes = new LinkedHashMap<>();

    public void adicionar(Candidato candidato) {
        candidatos.add(candidato);
    }

    public void descartar(String motivo) {
        descartes.merge(motivo, 1, Integer::sum);
    }

    public List<Candidato> getCandidatos() {
        return candidatos;
    }

    public Map<String, Integer> getDescartes() {
        return descartes;
    }

    public int totalDescartado() {
        return descartes.values().stream().mapToInt(Integer::intValue).sum();
    }
}
