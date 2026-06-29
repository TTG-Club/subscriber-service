package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.SubscriptionType;

import java.time.Instant;

/**
 * Сводный статус подписки пользователя для админ-карточки сайта (одна запись, а не список).
 * Берётся «самая релевантная» подписка: активная → ожидающая активации → истёкшая.
 *
 * @param active     есть ли действующая подписка прямо сейчас
 * @param registered есть ли у пользователя хоть одна подписка (в т.ч. не активированная/истёкшая)
 * @param status     статус релевантной подписки: ACTIVE | REGISTERED | EXPIRED (null — подписок нет)
 * @param type       тип релевантной подписки (null — подписок нет)
 * @param startsAt   старт релевантной подписки (null — не активирована / подписок нет)
 * @param expiresAt  окончание релевантной подписки (null — не активирована / подписок нет)
 */
public record AdminSubscriptionStatusResponse(
        boolean active,
        boolean registered,
        String status,
        SubscriptionType type,
        Instant startsAt,
        Instant expiresAt
) {
    /** Статус «подписок нет вовсе». */
    public static AdminSubscriptionStatusResponse none() {
        return new AdminSubscriptionStatusResponse(false, false, null, null, null, null);
    }
}
