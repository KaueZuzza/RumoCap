package br.com.rumocap.levantamento;

import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Certificados confiáveis para as conexões HTTPS com as fontes públicas.
 * <p>
 * Aceita os certificados raiz do Java e também os do sistema operacional
 * (Windows ou macOS). Alguns servidores do governo usam autoridades
 * certificadoras recentes que versões mais antigas do Java ainda não conhecem.
 * A validação continua completa (cadeia, validade e nome do servidor): apenas
 * a lista de raízes confiáveis é maior.
 */
final class ConfiancaTls {

    private static final Logger log = LoggerFactory.getLogger(ConfiancaTls.class);

    private ConfiancaTls() {
    }

    static SSLContext contexto() {
        List<X509ExtendedTrustManager> gerentes = new ArrayList<>();
        adicionar(gerentes, null, "Java");
        for (String loja : List.of("Windows-ROOT", "KeychainStore")) {
            try {
                KeyStore certificados = KeyStore.getInstance(loja);
                certificados.load(null, null);
                adicionar(gerentes, certificados, loja);
            } catch (Exception indisponivel) {
                // essa loja de certificados não existe neste sistema operacional
            }
        }
        try {
            SSLContext contexto = SSLContext.getInstance("TLS");
            contexto.init(null, new TrustManager[] {new Composto(gerentes)}, null);
            return contexto;
        } catch (GeneralSecurityException erro) {
            log.warn("Usando os certificados padrão do Java: {}", erro.getMessage());
            try {
                return SSLContext.getDefault();
            } catch (GeneralSecurityException semContexto) {
                throw new IllegalStateException(semContexto);
            }
        }
    }

    private static void adicionar(List<X509ExtendedTrustManager> gerentes, KeyStore certificados, String origem) {
        try {
            TrustManagerFactory fabrica = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            fabrica.init(certificados);
            for (TrustManager gerente : fabrica.getTrustManagers()) {
                if (gerente instanceof X509ExtendedTrustManager estendido) {
                    gerentes.add(estendido);
                }
            }
        } catch (GeneralSecurityException erro) {
            log.debug("Certificados de {} indisponíveis: {}", origem, erro.getMessage());
        }
    }

    /** Confia no certificado se qualquer uma das listas de raízes confiar (com as mesmas verificações). */
    private static final class Composto extends X509ExtendedTrustManager {

        private final List<X509ExtendedTrustManager> gerentes;

        Composto(List<X509ExtendedTrustManager> gerentes) {
            this.gerentes = List.copyOf(gerentes);
        }

        private interface Verificacao {
            void executar(X509ExtendedTrustManager gerente) throws CertificateException;
        }

        private void verificar(Verificacao verificacao) throws CertificateException {
            CertificateException ultimoErro = null;
            for (X509ExtendedTrustManager gerente : gerentes) {
                try {
                    verificacao.executar(gerente);
                    return;
                } catch (CertificateException erro) {
                    ultimoErro = erro;
                }
            }
            throw ultimoErro != null ? ultimoErro : new CertificateException("Nenhuma lista de certificados disponível.");
        }

        @Override
        public void checkServerTrusted(X509Certificate[] cadeia, String tipo, Socket conexao) throws CertificateException {
            verificar(gerente -> gerente.checkServerTrusted(cadeia, tipo, conexao));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] cadeia, String tipo, SSLEngine motor) throws CertificateException {
            verificar(gerente -> gerente.checkServerTrusted(cadeia, tipo, motor));
        }

        @Override
        public void checkServerTrusted(X509Certificate[] cadeia, String tipo) throws CertificateException {
            verificar(gerente -> gerente.checkServerTrusted(cadeia, tipo));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] cadeia, String tipo, Socket conexao) throws CertificateException {
            verificar(gerente -> gerente.checkClientTrusted(cadeia, tipo, conexao));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] cadeia, String tipo, SSLEngine motor) throws CertificateException {
            verificar(gerente -> gerente.checkClientTrusted(cadeia, tipo, motor));
        }

        @Override
        public void checkClientTrusted(X509Certificate[] cadeia, String tipo) throws CertificateException {
            verificar(gerente -> gerente.checkClientTrusted(cadeia, tipo));
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return gerentes.stream()
                    .flatMap(gerente -> Arrays.stream(gerente.getAcceptedIssuers()))
                    .toArray(X509Certificate[]::new);
        }
    }
}
