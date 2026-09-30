package br.com.rumocap.exception;

/** Muitas requisições em pouco tempo (resposta 429). */
public class LimiteExcedidoException extends RuntimeException {

    public LimiteExcedidoException(String mensagem) {
        super(mensagem);
    }
}
