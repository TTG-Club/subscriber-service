package club.ttg.subscriber.domain.subscription.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Запрос на погашение кода текущим пользователем.
 *
 * @param code код, который требуется погасить
 */
public record RedeemCodeRequest(
        @NotBlank String code
) {
}
