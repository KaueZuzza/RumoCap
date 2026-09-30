package br.com.rumocap.exception;

/** A operação conflita com dados já existentes, como um nome repetido (resposta 409). */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
