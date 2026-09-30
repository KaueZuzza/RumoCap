package br.com.rumocap.exception;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import br.com.rumocap.dto.ErroResponse;

/**
 * Converte os erros da aplicação em respostas JSON no formato {@link ErroResponse}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResponse> naoEncontrado(RecursoNaoEncontradoException erro) {
        return resposta(HttpStatus.NOT_FOUND, erro.getMessage());
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResponse> regraNegocio(RegraNegocioException erro) {
        return resposta(HttpStatus.BAD_REQUEST, erro.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResponse> conflito(ConflitoException erro) {
        return resposta(HttpStatus.CONFLICT, erro.getMessage());
    }

    @ExceptionHandler(LimiteExcedidoException.class)
    public ResponseEntity<ErroResponse> limiteExcedido(LimiteExcedidoException erro) {
        return resposta(HttpStatus.TOO_MANY_REQUESTS, erro.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResponse> dadosInvalidos(MethodArgumentNotValidException erro) {
        Map<String, String> campos = new LinkedHashMap<>();
        for (FieldError campo : erro.getBindingResult().getFieldErrors()) {
            campos.putIfAbsent(campo.getField(), campo.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErroResponse(400, "Verifique os campos destacados.", campos));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroResponse> corpoInvalido() {
        return resposta(HttpStatus.BAD_REQUEST, "Os dados enviados estão em um formato inválido.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErroResponse> parametroInvalido(MethodArgumentTypeMismatchException erro) {
        return resposta(HttpStatus.BAD_REQUEST, "Valor inválido para o parâmetro \"" + erro.getName() + "\".");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErroResponse> parametroAusente(MissingServletRequestParameterException erro) {
        return resposta(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: " + erro.getParameterName() + ".");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErroResponse> arquivoAusente() {
        return resposta(HttpStatus.BAD_REQUEST, "Selecione uma imagem para enviar.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErroResponse> arquivoGrandeDemais() {
        return resposta(HttpStatus.CONTENT_TOO_LARGE, "A imagem deve ter no máximo 5 MB.");
    }

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErroResponse> envioInvalido() {
        return resposta(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo enviado.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResponse> violacaoDeIntegridade() {
        return resposta(HttpStatus.CONFLICT,
                "A operação não pôde ser concluída porque entra em conflito com dados já cadastrados.");
    }

    private static ResponseEntity<ErroResponse> resposta(HttpStatus status, String mensagem) {
        return ResponseEntity.status(status).body(new ErroResponse(status.value(), mensagem));
    }
}
