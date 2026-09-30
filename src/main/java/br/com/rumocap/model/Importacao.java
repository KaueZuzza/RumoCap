package br.com.rumocap.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Uma execução do levantamento de estabelecimentos em uma fonte pública. */
@Entity
@Table(name = "importacao")
public class Importacao {

    public enum Situacao {
        EM_ANDAMENTO, CONCLUIDA, FALHOU
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chave da fonte: "osm", "cnes" ou "cnefe". */
    @Column(nullable = false, length = 40)
    private String fonte;

    @Column(name = "iniciada_em", nullable = false)
    private Instant iniciadaEm;

    @Column(name = "concluida_em")
    private Instant concluidaEm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Situacao situacao = Situacao.EM_ANDAMENTO;

    /** Registros aproveitáveis devolvidos pela fonte (após os filtros). */
    @Column(nullable = false)
    private int encontrados;

    @Column(nullable = false)
    private int novos;

    /** Registros já existentes que receberam informações novas. */
    @Column(nullable = false)
    private int atualizados;

    @Column(name = "sem_alteracao", nullable = false)
    private int semAlteracao;

    @Column(name = "possiveis_duplicados", nullable = false)
    private int possiveisDuplicados;

    @Column(name = "localizacao_pendente", nullable = false)
    private int localizacaoPendente;

    /** O mesmo local repetido dentro da própria fonte (não gera novo registro). */
    @Column(nullable = false)
    private int repetidos;

    /** Locais que o administrador já havia recusado (não voltam para a revisão). */
    @Column(name = "ja_recusados", nullable = false)
    private int jaRecusados;

    /** Registros da fonte descartados pelos filtros (sem nome, desativados etc.). */
    @Column(nullable = false)
    private int ignorados;

    /** Motivos dos descartes, um por linha ("Sem nome próprio: 120"). */
    @Column(length = 2000)
    private String detalhes;

    @Column(length = 1000)
    private String mensagem;

    protected Importacao() {
        // exigido pelo JPA
    }

    public Importacao(String fonte) {
        this.fonte = fonte;
        this.iniciadaEm = Instant.now();
    }

    public void concluir(String mensagemFinal) {
        this.situacao = Situacao.CONCLUIDA;
        this.concluidaEm = Instant.now();
        this.mensagem = mensagemFinal;
    }

    public void falhar(String motivo) {
        this.situacao = Situacao.FALHOU;
        this.concluidaEm = Instant.now();
        this.mensagem = motivo;
    }

    public Long getId() {
        return id;
    }

    public String getFonte() {
        return fonte;
    }

    public Instant getIniciadaEm() {
        return iniciadaEm;
    }

    public Instant getConcluidaEm() {
        return concluidaEm;
    }

    public Situacao getSituacao() {
        return situacao;
    }

    public int getEncontrados() {
        return encontrados;
    }

    public void setEncontrados(int encontrados) {
        this.encontrados = encontrados;
    }

    public int getNovos() {
        return novos;
    }

    public void setNovos(int novos) {
        this.novos = novos;
    }

    public int getAtualizados() {
        return atualizados;
    }

    public void setAtualizados(int atualizados) {
        this.atualizados = atualizados;
    }

    public int getSemAlteracao() {
        return semAlteracao;
    }

    public void setSemAlteracao(int semAlteracao) {
        this.semAlteracao = semAlteracao;
    }

    public int getPossiveisDuplicados() {
        return possiveisDuplicados;
    }

    public void setPossiveisDuplicados(int possiveisDuplicados) {
        this.possiveisDuplicados = possiveisDuplicados;
    }

    public int getLocalizacaoPendente() {
        return localizacaoPendente;
    }

    public void setLocalizacaoPendente(int localizacaoPendente) {
        this.localizacaoPendente = localizacaoPendente;
    }

    public int getRepetidos() {
        return repetidos;
    }

    public void setRepetidos(int repetidos) {
        this.repetidos = repetidos;
    }

    public int getJaRecusados() {
        return jaRecusados;
    }

    public void setJaRecusados(int jaRecusados) {
        this.jaRecusados = jaRecusados;
    }

    public int getIgnorados() {
        return ignorados;
    }

    public void setIgnorados(int ignorados) {
        this.ignorados = ignorados;
    }

    public String getDetalhes() {
        return detalhes;
    }

    public void setDetalhes(String detalhes) {
        this.detalhes = detalhes;
    }

    public String getMensagem() {
        return mensagem;
    }
}
