package club.ttg.subscriber.exception;

import java.time.Instant;

/**
 * Унифицированное тело ответа об ошибке API.
 *
 * @param timestamp момент формирования ответа
 * @param status    HTTP-код ответа
 * @param error     текстовое название статуса (reason phrase)
 * @param message   человекочитаемое сообщение об ошибке для клиента
 */
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message
) {
}
