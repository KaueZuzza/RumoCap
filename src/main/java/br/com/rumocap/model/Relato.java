package br.com.rumocap.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Aviso de "informação incorreta" enviado por um visitante na página do
 * estabelecimento. O administrador confere e corrige o cadastro.
 */
@Entity
@Table(name = "relato")
public class Relato {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "estabelecimento_id", nullable = false)
    private Long estabelecimentoId;

    @Column(nullable = false, length = 1000)
    private String mensagem;

    /** Contato opcional de quem enviou (e-mail ou telefone), para esclarecer dúvidas. */
    @Column(length = 120)
    private String contato;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(nullable = false)
    private boolean resolvido;

    @Column(name = "resolvido_em")
    private Instant resolvidoEm;

    protected Relato() {
        // exigido pelo JPA
    }

    public Relato(Long estabelecimentoId, String mensagem, String contato) {
        this.estabelecimentoId = estabelecimentoId;
        this.mensagem = mensagem;
        this.contato = contato;
        this.criadoEm = Instant.now();
    }

    public void marcarResolvido() {
        this.resolvido = true;
        this.resolvidoEm = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getEstabelecimentoId() {
        return estabelecimentoId;
    }

    public String getMensagem() {
        return mensagem;
    }

    public String getContato() {
        return contato;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public boolean isResolvido() {
        return resolvido;
    }

    public Instant getResolvidoEm() {
        return resolvidoEm;
    }
}
