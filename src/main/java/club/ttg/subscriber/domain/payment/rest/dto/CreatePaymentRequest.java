package club.ttg.subscriber.domain.payment.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Запрос на оплату подписки. Верхняя граница срока задаётся настройкой
 * {@code payanyway.max-months} и проверяется в сервисе.
 *
 * @param months сколько месяцев оплатить (не меньше 1)
 */
public record CreatePaymentRequest(
        @NotNull @Min(1) Integer months
) {
}
