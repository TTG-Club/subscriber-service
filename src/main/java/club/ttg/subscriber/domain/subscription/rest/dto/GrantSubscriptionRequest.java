package club.ttg.subscriber.domain.subscription.rest.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Админская выдача/продление подписки на N месяцев.
 *
 * @param months срок выдачи/продления в месяцах (не меньше 1)
 */
public record GrantSubscriptionRequest(
        @NotNull @Min(1) Integer months
) {
}
