package club.ttg.subscriber.domain.subscription.repository;

import club.ttg.subscriber.domain.subscription.model.UserSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Доступ к подпискам пользователей.
 */
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, UUID> {
    /** Все подписки, новые сверху (для админского списка). */
    List<UserSubscription> findAllByOrderByCreatedAtDesc();

    /** Подписки пользователя, новые сверху (для личного кабинета). */
    List<UserSubscription> findByOwnerUsernameOrderByCreatedAtDesc(String ownerUsername);

    /** Заводил ли пользователь хотя бы одну подписку (без учёта активности). */
    boolean existsByOwnerUsername(String ownerUsername);

    /**
     * Активная подписка пользователя с наибольшей датой окончания.
     * Активной считается подписка с проставленным `startsAt` и `expiresAt` в будущем.
     */
    Optional<UserSubscription> findFirstByOwnerUsernameAndStartsAtIsNotNullAndExpiresAtAfterOrderByExpiresAtDesc(
            String ownerUsername, Instant now);
}
