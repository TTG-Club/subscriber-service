package club.ttg.subscriber.domain.subscription.rest.dto;

import jakarta.validation.constraints.NotBlank;

public record RedeemCodeRequest(
        @NotBlank String code
) {
}
