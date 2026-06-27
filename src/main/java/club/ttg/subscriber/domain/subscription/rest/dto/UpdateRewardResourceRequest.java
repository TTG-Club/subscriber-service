package club.ttg.subscriber.domain.subscription.rest.dto;

import club.ttg.subscriber.domain.subscription.model.RewardResourceAvailability;
import jakarta.validation.constraints.NotNull;

/**
 * Обновление контента награды (например, включить приключение, когда готово).
 */
public record UpdateRewardResourceRequest(
        String title,
        String url,
        @NotNull RewardResourceAvailability availability,
        String note
) {
}
