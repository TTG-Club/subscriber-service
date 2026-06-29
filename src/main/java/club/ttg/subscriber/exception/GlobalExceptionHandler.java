package club.ttg.subscriber.exception;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

/**
 * Глобальный обработчик исключений: приводит ошибки всех ручек к единому
 * {@link ApiErrorResponse} с корректным HTTP-статусом.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {
    /** Прикладные исключения: отдаёт заложенные в них статус и сообщение. */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException exception) {
        return buildResponse(exception.getStatus(), exception.getMessage());
    }

    /** Исключения Spring с HTTP-статусом; пустая причина заменяется дефолтным текстом. */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException exception) {
        String message = exception.getReason() == null || exception.getReason().isBlank()
                ? "Ошибка выполнения запроса"
                : exception.getReason();

        return buildResponse(exception.getStatusCode(), message);
    }

    /** Ошибки bean-валидации: возвращает 400 с сообщением первого нарушенного поля. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .findFirst().filter(error -> error.getDefaultMessage() != null)
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .orElse("Некорректные данные запроса");

        return buildResponse(HttpStatus.BAD_REQUEST, message);
    }

    /** Нарушения целостности данных (например, уникальных ограничений): возвращает 409. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException exception) {
        return buildResponse(HttpStatus.CONFLICT, "Нарушение целостности данных");
    }

    private ResponseEntity<ApiErrorResponse> buildResponse(HttpStatusCode statusCode, String message) {
        HttpStatus status = HttpStatus.resolve(statusCode.value());
        String error = status == null ? "Error" : status.getReasonPhrase();

        return ResponseEntity
                .status(statusCode)
                .body(new ApiErrorResponse(
                        Instant.now(),
                        statusCode.value(),
                        error,
                        message
                ));
    }
}
