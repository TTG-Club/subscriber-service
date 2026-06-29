package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.RewardPerk;
import club.ttg.subscriber.domain.subscription.model.RewardTier;
import club.ttg.subscriber.domain.subscription.model.SubscriptionType;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Полное представление выпущенного кода для админки: содержимое, кем/когда погашен,
 * признак деактивации. В отличие от пользовательского {@link MyRedemptionResponse},
 * раскрывает все служебные поля и не резолвит награды в ссылки.
 *
 * @param id                 идентификатор кода
 * @param code               сам код
 * @param subscriptionType   тип подписки кода (null — код без подписки)
 * @param subscriptionMonths срок подписки в месяцах (null — код без подписки)
 * @param rewardTier         краудфандинговый тир-пресет (null — без пресета)
 * @param perks              косметические перки кода помимо тира
 * @param achievements       коды достижений кода
 * @param label              пометка админа, заданная при выпуске
 * @param redeemedBy         username пользователя, погасившего код (null — не погашен)
 * @param redeemedAt         момент погашения (null — не погашен)
 * @param disabled           деактивирован ли код (нельзя погасить)
 * @param disabledAt         момент деактивации (null — не деактивирован)
 * @param disabledBy         кто деактивировал код (null — не деактивирован)
 * @param createdAt          момент выпуска кода
 */
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
