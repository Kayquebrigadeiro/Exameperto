package br.com.exameperto.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class RegistrationErrorHandler {
    @ExceptionHandler(RegistrationService.RegistrationException.class)
    ResponseEntity<ApiError> registration(RegistrationService.RegistrationException ex) { return ResponseEntity.status(ex.getStatusCode()).body(ex.body()); }

    @ExceptionHandler(AuthService.AuthException.class)
    ResponseEntity<ApiError> auth(AuthService.AuthException ex) { return ResponseEntity.status(ex.getStatusCode()).header("Cache-Control", "no-store").body(ex.body()); }

    @ExceptionHandler(RepresentationService.RepresentationException.class)
    ResponseEntity<ApiError> representation(RepresentationService.RepresentationException ex) { return ResponseEntity.status(ex.getStatusCode()).header("Cache-Control", "no-store").body(ex.body()); }

    @ExceptionHandler(DelivererService.DelivererException.class)
    ResponseEntity<ApiError> deliverer(DelivererService.DelivererException ex) { return ResponseEntity.status(ex.getStatusCode()).header("Cache-Control", "no-store").body(ex.body()); }

    @ExceptionHandler(BenefitService.BenefitServiceException.class)
    ResponseEntity<ApiError> benefit(BenefitService.BenefitServiceException ex) { return ResponseEntity.status(ex.getStatusCode()).header("Cache-Control", "no-store").body(ex.body()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException ignored) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", "Confira os campos informados.", UUID.randomUUID(), false));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> malformed(HttpMessageNotReadableException ignored) {
        return ResponseEntity.badRequest().body(new ApiError("INVALID_INPUT", "O corpo da requisição é inválido.", UUID.randomUUID(), false));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> uploadTooLarge(MaxUploadSizeExceededException ignored) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).header("Cache-Control", "no-store")
            .body(new ApiError("FILE_TOO_LARGE", "Arquivo acima do limite permitido.", UUID.randomUUID(), false));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ignored) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError("INTERNAL_ERROR", "Não foi possível concluir a solicitação.", UUID.randomUUID(), true));
    }
}
