package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.SubscriptionType;

import java.time.Instant;

/**
 * Реал-тайм статус подписки пользователя для проверок доступа (фронт и service-to-service).
 * `active` истинно, если есть подписка с проставленным `startsAt` и `expiresAt` в будущем.
 *
 * @param active     активна ли подписка прямо сейчас (startsAt проставлен, expiresAt в будущем)
 * @param registered есть ли у пользователя хоть одна подписка (в т.ч. не активированная) —
 *                   нужно VTTG-гейту, чтобы отличать «нет подписки вовсе» от «есть, но не активна»
 * @param expiresAt  дата окончания активной подписки (null, если активной нет)
 * @param startsAt   дата старта активной подписки (null, если активной нет)
 * @param type       тип активной подписки (null, если активной нет)
 */
public record SubscriptionStatusResponse(
        boolean active,
        boolean registered,
        Instant expiresAt,
        Instant startsAt,
        SubscriptionType type
) {
    /** Статус «нет подписки вовсе». */
    public static SubscriptionStatusResponse inactive() {
        return new SubscriptionStatusResponse(false, false, null, null, null);
    }
}
