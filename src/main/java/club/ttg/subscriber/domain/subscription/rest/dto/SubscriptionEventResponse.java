package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.SubscriptionEventType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Событие истории подписки для админ-карточки пользователя.
 *
 * @param id            идентификатор события
 * @param type          что произошло
 * @param months        на сколько месяцев (null — для отключения)
 * @param expiresBefore окончание действующей подписки до события (null — подписки не было)
 * @param expiresAfter  окончание действующей подписки после события (null — подписки нет)
 * @param actorUsername кто совершил действие
 * @param code          промокод, если событие связано с ним
 * @param amount        сумма оплаты в рублях, если событие — оплата
 * @param createdAt     момент события
 */
public record SubscriptionEventResponse(
        UUID id,
        SubscriptionEventType type,
        Integer months,
        Instant expiresBefore,
        Instant expiresAfter,
        String actorUsername,
        String code,
        BigDecimal amount,
        Instant createdAt
) {
}
