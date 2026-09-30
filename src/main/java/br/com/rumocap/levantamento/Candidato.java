package br.com.rumocap.levantamento;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estabelecimento encontrado em uma fonte pública, antes de ir para o banco.
 * Campos não informados pela fonte ficam {@code null}: nada é completado.
 */
public final class Candidato {

    private final String fonteId;
    private final String nome;
    private final String categoria;
    private String urlFonte;
    private String tipoFonte;
    private String descricao;
    private String endereco;
    private String telefone;
    private String whatsapp;
    private String site;
    private String contato;
    private String horario;
    private Double latitude;
    private Double longitude;
    private final List<String> observacoes = new ArrayList<>();
    private final Map<String, Object> dadosFonte = new LinkedHashMap<>();

    /**
     * @param fonteId   identificador estável na fonte (ex.: "osm:node/123")
     * @param nome      nome do estabelecimento, como consta na fonte
     * @param categoria nome da categoria do RumoCap ("Saúde", "Outros"...)
     */
    public Candidato(String fonteId, String nome, String categoria) {
        this.fonteId = fonteId;
        this.nome = nome;
        this.categoria = categoria;
    }

    public void localizacao(Double novaLatitude, Double novaLongitude) {
        this.latitude = novaLatitude;
        this.longitude = novaLongitude;
    }

    public void descartarLocalizacao(String motivo) {
        this.latitude = null;
        this.longitude = null;
        observar(motivo);
    }

    public boolean possuiLocalizacao() {
        return latitude != null && longitude != null;
    }

    public void observar(String observacao) {
        if (observacao != null && !observacao.isBlank() && !observacoes.contains(observacao)) {
            observacoes.add(observacao);
        }
    }

    public void guardar(String chave, Object valor) {
        if (valor != null && !(valor instanceof String texto && texto.isBlank())) {
            dadosFonte.put(chave, valor);
        }
    }

    public String getFonteId() {
        return fonteId;
    }

    public String getNome() {
        return nome;
    }

    public String getCategoria() {
        return categoria;
    }

    public String getUrlFonte() {
        return urlFonte;
    }

    public void setUrlFonte(String urlFonte) {
        this.urlFonte = urlFonte;
    }

    public String getTipoFonte() {
        return tipoFonte;
    }

    public void setTipoFonte(String tipoFonte) {
        this.tipoFonte = tipoFonte;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(String whatsapp) {
        this.whatsapp = whatsapp;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getContato() {
        return contato;
    }

    public void setContato(String contato) {
        this.contato = contato;
    }

    public String getHorario() {
        return horario;
    }

    public void setHorario(String horario) {
        this.horario = horario;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public List<String> getObservacoes() {
        return observacoes;
    }

    public Map<String, Object> getDadosFonte() {
        return dadosFonte;
    }
}
