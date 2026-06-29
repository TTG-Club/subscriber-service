package club.ttg.subscriber.domain.subscription.service;

import club.ttg.subscriber.domain.subscription.model.UserSubscription;
import club.ttg.subscriber.domain.subscription.repository.UserSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Отражает истечение подписок по датам. Ролей сервис не выдаёт и не снимает: доступ
 * считается в реальном времени по `startsAt`/`expiresAt` (см. {@code SubscriptionService.statusFor}),
 * поэтому отдельная мутация БД при истечении не нужна. Джоба оставлена логирующей —
 * как точка наблюдаемости и место для будущих побочных эффектов (нотификации и т.п.).
 */
@Slf4j
@RequiredArgsConstructor
@Component
public class SubscriptionExpiryScheduler {
    private final UserSubscriptionRepository subscriptionRepository;

    /**
     * Ежедневно в 03:30 по серверному времени.
     */
    @Scheduled(cron = "0 30 3 * * *")
    @Transactional(readOnly = true)
    public void reflectExpiredSubscriptions() {
        Instant now = Instant.now();
        long expired = subscriptionRepository.findAll().stream()
                .filter(subscription -> isExpired(subscription, now))
                .count();
        if (expired > 0) {
            log.info("Истёкших подписок на {}: {} (статус вычисляется в реальном времени)", now, expired);
        }
    }

    private boolean isExpired(UserSubscription subscription, Instant now) {
        return subscription.getStartsAt() != null
                && subscription.getExpiresAt() != null
                && !subscription.getExpiresAt().isAfter(now);
    }
}
