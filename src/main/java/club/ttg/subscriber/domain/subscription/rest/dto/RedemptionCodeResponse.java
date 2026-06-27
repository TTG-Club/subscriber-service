package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.RewardPerk;
import club.ttg.subscriber.domain.subscription.model.RewardTier;
import club.ttg.subscriber.domain.subscription.model.SubscriptionType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record RedemptionCodeResponse(
        UUID id,
        String code,
        SubscriptionType subscriptionType,
        Integer subscriptionMonths,
        RewardTier rewardTier,
        Set<RewardPerk> perks,
        Set<String> achievements,
        String label,
        String redeemedBy,
        Instant redeemedAt,
        boolean disabled,
        Instant disabledAt,
        String disabledBy,
        Instant createdAt
) {
}
