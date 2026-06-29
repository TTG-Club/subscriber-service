package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.SubscriptionType;

import java.time.Instant;
import java.util.UUID;

/**
 * Представление одной подписки пользователя для фронта (личный кабинет, админ-список).
 *
 * @param id             идентификатор подписки
 * @param type           тип подписки
 * @param status         вычисляемый статус: CREATED | REGISTERED | ACTIVE | EXPIRED
 * @param durationMonths срок подписки в месяцах
 * @param ownerUsername  username владельца (null — подписка ещё не закреплена за пользователем)
 * @param registeredAt   момент закрепления подписки за пользователем (null — не закреплена)
 * @param startsAt       момент активации (null — не активирована)
 * @param expiresAt      момент окончания (null — не активирована)
 * @param createdAt      момент создания записи
 * @param updatedAt      момент последнего изменения записи
 */
public record SubscriptionResponse(
        UUID id,
        SubscriptionType type,
        String status,
        Integer durationMonths,
        String ownerUsername,
        Instant registeredAt,
        Instant startsAt,
        Instant expiresAt,
        Instant createdAt,
        Instant updatedAt
) {
}
