package club.ttg.subscriber.domain.payment.rest.dto;

/**
 * Включена ли оплата подписки.
 *
 * @param enabled {@code false} — оплата выключена в админке, платежи не создаются
 */
public record PaymentAvailabilityResponse(boolean enabled) {
}
