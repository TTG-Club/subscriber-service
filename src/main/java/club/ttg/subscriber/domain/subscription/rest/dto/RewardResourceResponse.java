package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.RewardPerk;
import club.ttg.subscriber.domain.subscription.model.RewardResourceAvailability;

import java.time.Instant;

/**
 * Конфиг контента одной награды для админки: ссылка, статус готовности и заметка.
 *
 * @param perk         перк, к которому привязан контент
 * @param title        отображаемое название награды
 * @param url          ссылка на контент награды (null — ещё не задана)
 * @param availability готовность контента к выдаче
 * @param note         служебная заметка админа (null — нет)
 * @param updatedAt    момент последнего изменения контента
 */
public record RewardResourceResponse(
        RewardPerk perk,
        String title,
        String url,
        RewardResourceAvailability availability,
        String note,
        Instant updatedAt
) {
}
