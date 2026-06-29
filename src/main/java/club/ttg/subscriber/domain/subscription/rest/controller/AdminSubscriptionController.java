package club.ttg.subscriber.domain.subscription.rest.controller;

import club.ttg.subscriber.domain.subscription.rest.dto.AdminSubscriptionStatusResponse;
import club.ttg.subscriber.domain.subscription.rest.dto.GrantSubscriptionRequest;
import club.ttg.subscriber.domain.subscription.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Админское управление подписками пользователей. Идентификатор пользователя `{userId}`
 * трактуется как его username из auth-service (так подписки хранятся в `owner_username`).
 * Все три ручки возвращают единый сводный статус — это и читает админ-карточка сайта.
 */
@Tag(name = "Подписки (админ)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/subscriptions")
public class AdminSubscriptionController {
    private final SubscriptionService subscriptionService;

    @Secured("ADMIN")
    @Operation(summary = "Сводный статус подписки пользователя (период, тип, активна ли)")
    @GetMapping("/{userId}")
    public AdminSubscriptionStatusResponse status(@PathVariable String userId) {
        return subscriptionService.adminStatusFor(userId);
    }

    @Secured("ADMIN")
    @Operation(summary = "Выдать/продлить подписку пользователя на N месяцев")
    @PutMapping("/{userId}/grant")
    public AdminSubscriptionStatusResponse grant(@PathVariable String userId,
                                                 @Valid @RequestBody GrantSubscriptionRequest request) {
        subscriptionService.grant(userId, request.months());

        return subscriptionService.adminStatusFor(userId);
    }

    @Secured("ADMIN")
    @Operation(summary = "Немедленно отключить активные подписки пользователя")
    @DeleteMapping("/{userId}")
    public AdminSubscriptionStatusResponse revoke(@PathVariable String userId) {
        subscriptionService.revoke(userId);

        return subscriptionService.adminStatusFor(userId);
    }
}
