package club.ttg.subscriber.domain.payment.rest.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Включение или выключение оплаты подписки из админки.
 *
 * @param enabled новое состояние оплаты
 */
public record UpdatePaymentAvailabilityRequest(
        @NotNull(message = "Укажите, включена ли оплата")
        Boolean enabled
) {
}
