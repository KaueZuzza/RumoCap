package br.com.rumocap.levantamento;

/** A fonte pública não respondeu ou devolveu dados em formato inesperado. */
public class FonteIndisponivelException extends RuntimeException {

    public FonteIndisponivelException(String mensagem) {
        super(mensagem);
    }

    public FonteIndisponivelException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
