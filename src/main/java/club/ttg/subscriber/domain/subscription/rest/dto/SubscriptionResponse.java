package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.SubscriptionType;

import java.time.Instant;
import java.util.UUID;

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
