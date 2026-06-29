package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.RewardTier;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Погашенный текущим пользователем код вместе с его содержимым для личного кабинета.
 * В отличие от админского {@link RedemptionCodeResponse}, не раскрывает служебные поля,
 * зато сразу резолвит награды в ссылки/статусы готовности.
 * <p>
 * Достижения отдаются как набор кодов: каталог достижений живёт в core-api, не здесь.
 *
 * @param id           идентификатор кода
 * @param code         сам погашенный код (виден владельцу)
 * @param redeemedAt   момент погашения
 * @param rewardTier   тир-пресет кода (null — без пресета)
 * @param rewards      перки кода (пресет тира ∪ доп. перки) со ссылками и статусом
 * @param achievements коды достижений кода
 * @param subscription подписка, созданная этим кодом; null — код без подписки
 */
public record MyRedemptionResponse(
        UUID id,
        String code,
        Instant redeemedAt,
        RewardTier rewardTier,
        List<UserRewardResponse> rewards,
        Set<String> achievements,
        SubscriptionResponse subscription
) {
}
