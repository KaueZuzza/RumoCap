package br.com.rumocap.exception;

/** Os dados enviados não atendem a uma regra do sistema (resposta 400). */
public class RegraNegocioException extends RuntimeException {

    public RegraNegocioException(String mensagem) {
        super(mensagem);
    }
}
