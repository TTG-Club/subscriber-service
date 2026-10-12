package club.ttg.subscriber.domain.payment.rest.dto;

import java.util.UUID;

/**
 * Созданный платёж за подписку.
 *
 * @param id         идентификатор платежа ({@code MNT_TRANSACTION_ID})
 * @param paymentUrl подписанная ссылка на платёжную форму PayAnyWay, куда нужно перейти
 * @param testMode   создан ли платёж в тестовом режиме (без реального списания)
 */
public record CreatePaymentResponse(
        UUID id,
        String paymentUrl,
        boolean testMode
) {
}
