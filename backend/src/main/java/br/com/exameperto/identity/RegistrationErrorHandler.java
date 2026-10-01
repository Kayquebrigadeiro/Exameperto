package br.com.exameperto.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
public class RegistrationErrorHandler {
    @ExceptionHandler(RegistrationService.RegistrationException.class)
    ResponseEntity<ApiError> registration(RegistrationService.RegistrationException ex) { return ResponseEntity.status(ex.getStatusCode()).body(ex.body()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ignored) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", "Confira os campos informados.", UUID.randomUUID(), false));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(HttpMessageNotReadableException ignored) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", "O corpo da requisição é inválido.", UUID.randomUUID(), false));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ignored) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR", "Não foi possível concluir o cadastro.", UUID.randomUUID(), true));
    }
}
