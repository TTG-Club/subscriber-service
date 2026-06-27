package club.ttg.subscriber.domain.subscription.rest.controller;

import club.ttg.subscriber.domain.subscription.rest.dto.SubscriptionStatusResponse;
import club.ttg.subscriber.domain.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service статус подписки. Зовётся core-api (VttgAccessService) и auth-service.
 * Защищён не JWT, а заголовком `X-Service-Token` (см. InternalServiceTokenFilter), поэтому
 * метод без `@Secured`. Идентификатор пользователя — это его username из auth-service.
 */
@Tag(name = "Подписки (внутреннее)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/subscriptions")
public class InternalSubscriptionController {
    private final SubscriptionService subscriptionService;

    @Operation(summary = "Реал-тайм статус подписки пользователя (service-to-service)")
    @GetMapping("/{userId}/status")
    public SubscriptionStatusResponse status(@PathVariable String userId) {
        return subscriptionService.statusFor(userId);
    }
}
