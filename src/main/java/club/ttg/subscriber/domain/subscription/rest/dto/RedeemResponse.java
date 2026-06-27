package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.RewardPerk;

import java.util.List;

/**
 * Итог погашения кода: созданная подписка (если код её нёс), выданные перки и достижения.
 * Возвращает полный перечень начисленного, чтобы фронт показал модалку «что получено».
 * <p>
 * Достижения в этом сервисе не хранятся: возвращаются коды достижений, которые
 * сервис попытался начислить в core-api (best-effort, см. CoreApiAchievementClient).
 *
 * @param subscription        подписка в статусе REGISTERED; null, если код без подписки
 * @param grantedPerks        фактически выданные перки (без уже имевшихся у пользователя)
 * @param grantedAchievements коды достижений кода, отправленные в core-api на начисление
 */
public record RedeemResponse(
        SubscriptionResponse subscription,
        List<RewardPerk> grantedPerks,
        List<String> grantedAchievements
) {
}
