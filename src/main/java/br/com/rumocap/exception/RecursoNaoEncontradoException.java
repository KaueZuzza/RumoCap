package br.com.rumocap.exception;

/** O registro procurado não existe (resposta 404). */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
