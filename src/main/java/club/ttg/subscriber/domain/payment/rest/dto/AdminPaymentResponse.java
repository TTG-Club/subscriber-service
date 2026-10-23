package club.ttg.subscriber.domain.payment.rest.dto;

import club.ttg.subscriber.domain.payment.model.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Платёж за подписку для админской статистики покупок.
 *
 * @param id             идентификатор платежа
 * @param username       кто платил (username из auth-service)
 * @param email          почта на момент оплаты (на неё ушёл чек)
 * @param amount         сумма платежа
 * @param currency       валюта
 * @param months         на сколько месяцев продлевается подписка
 * @param status         CREATED | PAID | REJECTED
 * @param testMode       тестовый платёж без реального списания
 * @param operationId    номер операции в MONETA.RU (null — оплаты ещё не было)
 * @param subscriptionId подписка, выданная по платежу (null — не выдана)
 * @param paidAt         момент подтверждения оплаты
 * @param createdAt      момент создания платежа
 */
public record AdminPaymentResponse(
        UUID id,
        String username,
        String email,
        BigDecimal amount,
        String currency,
        Integer months,
        PaymentStatus status,
        boolean testMode,
        String operationId,
        UUID subscriptionId,
        Instant paidAt,
        Instant createdAt
) {
}
