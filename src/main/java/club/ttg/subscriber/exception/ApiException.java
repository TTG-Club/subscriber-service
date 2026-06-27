package club.ttg.subscriber.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Прикладное исключение с HTTP-статусом и сообщением для клиента.
 */
@Getter
public class ApiException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus status;

    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
