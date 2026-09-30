package br.com.rumocap.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * Estabelecimento comercial ou prestador de serviço de Capitão Poço.
 * <p>
 * Os registros vêm do cadastro manual da administração ou do levantamento em
 * fontes públicas (OpenStreetMap, CNES, CNEFE/IBGE). Os levantados entram como
 * {@link SituacaoRevisao#PENDENTE} e só aparecem no guia depois de aprovados.
 */
@Entity
@Table(name = "estabelecimento")
public class Estabelecimento {

    /** Fonte gravada nos registros cadastrados pela área administrativa. */
    public static final String FONTE_CADASTRO_MANUAL = "Cadastro manual";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(length = 400)
    private String descricao;

    @Column(length = 255)
    private String endereco;

    @Column(length = 40)
    private String telefone;

    @Column(length = 40)
    private String whatsapp;

    /** Site ou rede social principal (endereço completo, com https://). */
    @Column(length = 255)
    private String site;

    /** Outros contatos (e-mail, redes sociais), um por linha. */
    @Column(length = 500)
    private String contato;

    /** Horário de funcionamento em texto livre, por exemplo "Seg. a sex.: 8h às 18h". */
    @Column(length = 500)
    private String horario;

    private Double latitude;

    private Double longitude;

    /** Nome do arquivo da imagem/logo gravado na pasta de uploads (opcional). */
    @Column(length = 255)
    private String imagem;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private Categoria categoria;

    /** De onde a informação veio, por exemplo "OpenStreetMap" ou "Cadastro manual". */
    @Column(nullable = false, length = 80)
    private String fonte = FONTE_CADASTRO_MANUAL;

    /** Página do registro na fonte, quando existe. */
    @Column(name = "url_fonte", length = 500)
    private String urlFonte;

    /** Identificador do registro na fonte (ex.: "osm:node/123"), usado nas atualizações. */
    @Column(name = "fonte_id", length = 120, unique = true)
    private String fonteId;

    /** Tipo informado pela fonte (ex.: "amenity=pharmacy"), usado na categorização. */
    @Column(name = "tipo_fonte", length = 200)
    private String tipoFonte;

    /** Dados originais da fonte em JSON, guardados para conferência. */
    @Column(name = "dados_fonte", length = 4000)
    private String dadosFonte;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SituacaoRevisao situacao = SituacaoRevisao.APROVADO;

    /** Falso quando o estabelecimento foi arquivado (fica fora do guia, mas não é apagado). */
    @Column(nullable = false)
    private boolean ativo = true;

    /** Verdadeiro quando o administrador conferiu a posição no mapa. */
    @Column(name = "localizacao_validada", nullable = false)
    private boolean localizacaoValidada;

    /** Registro semelhante encontrado na importação (possível duplicado), a conferir. */
    @Column(name = "duplicado_de_id")
    private Long duplicadoDeId;

    /** Observações da importação para a revisão (alertas, dados descartados etc.). */
    @Column(name = "observacao_revisao", length = 1000)
    private String observacaoRevisao;

    @Column(name = "criado_em")
    private Instant criadoEm;

    /** Última mudança nos dados do estabelecimento (não muda com aprovações ou importações sem novidade). */
    @Column(name = "atualizado_em")
    private Instant atualizadoEm;

    /** Última importação em que o registro ainda constava na fonte. */
    @Column(name = "visto_na_fonte_em")
    private Instant vistoNaFonteEm;

    @Column(name = "revisado_em")
    private Instant revisadoEm;

    @PrePersist
    void aoCriar() {
        Instant agora = Instant.now();
        if (criadoEm == null) {
            criadoEm = agora;
        }
        atualizadoEm = agora;
    }

    /** Registra que os dados do estabelecimento mudaram (edição, imagem ou informação nova da fonte). */
    public void marcarAtualizado() {
        atualizadoEm = Instant.now();
    }

    public boolean possuiLocalizacao() {
        return latitude != null && longitude != null;
    }

    /** Aprovado e ativo: aparece no guia público. */
    public boolean isPublico() {
        return situacao == SituacaoRevisao.APROVADO && ativo;
    }

    public void definirLocalizacao(Double novaLatitude, Double novaLongitude) {
        this.latitude = novaLatitude;
        this.longitude = novaLongitude;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
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

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getImagem() {
        return imagem;
    }

    public void setImagem(String imagem) {
        this.imagem = imagem;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public String getFonte() {
        return fonte;
    }

    public void setFonte(String fonte) {
        this.fonte = fonte;
    }

    public String getUrlFonte() {
        return urlFonte;
    }

    public void setUrlFonte(String urlFonte) {
        this.urlFonte = urlFonte;
    }

    public String getFonteId() {
        return fonteId;
    }

    public void setFonteId(String fonteId) {
        this.fonteId = fonteId;
    }

    public String getTipoFonte() {
        return tipoFonte;
    }

    public void setTipoFonte(String tipoFonte) {
        this.tipoFonte = tipoFonte;
    }

    public String getDadosFonte() {
        return dadosFonte;
    }

    public void setDadosFonte(String dadosFonte) {
        this.dadosFonte = dadosFonte;
    }

    public SituacaoRevisao getSituacao() {
        return situacao;
    }

    public void setSituacao(SituacaoRevisao situacao) {
        this.situacao = situacao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean isLocalizacaoValidada() {
        return localizacaoValidada;
    }

    public void setLocalizacaoValidada(boolean localizacaoValidada) {
        this.localizacaoValidada = localizacaoValidada;
    }

    public Long getDuplicadoDeId() {
        return duplicadoDeId;
    }

    public void setDuplicadoDeId(Long duplicadoDeId) {
        this.duplicadoDeId = duplicadoDeId;
    }

    public String getObservacaoRevisao() {
        return observacaoRevisao;
    }

    public void setObservacaoRevisao(String observacaoRevisao) {
        this.observacaoRevisao = observacaoRevisao;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public Instant getAtualizadoEm() {
        return atualizadoEm;
    }

    public Instant getVistoNaFonteEm() {
        return vistoNaFonteEm;
    }

    public void setVistoNaFonteEm(Instant vistoNaFonteEm) {
        this.vistoNaFonteEm = vistoNaFonteEm;
    }

    public Instant getRevisadoEm() {
        return revisadoEm;
    }

    public void setRevisadoEm(Instant revisadoEm) {
        this.revisadoEm = revisadoEm;
    }
}
